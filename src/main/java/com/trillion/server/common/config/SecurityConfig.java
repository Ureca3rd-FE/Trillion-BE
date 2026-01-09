package com.trillion.server.common.config;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trillion.server.auth.dto.LoginResponse;
import com.trillion.server.auth.service.AuthService;
import com.trillion.server.common.exception.ErrorMessages;
import com.trillion.server.common.exception.ErrorResponse;
import com.trillion.server.common.exception.SuccessResponse;
import com.trillion.server.common.filter.JwtAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final Logger logger = LoggerFactory.getLogger(SecurityConfig.class);

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    
    @Autowired
    @Lazy
    private CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository;
    
    @Value("${jwt.access-token-expiration:3600000}")
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;
    
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;
    
    @Value("${app.oauth2.hmac-secret:${jwt.secret}}")
    private String oauth2HmacSecret;
    
    @Value("${app.oauth2.allowed-redirect-uris:http://localhost:3000,http://localhost:5173}")
    private String allowedRedirectUrisConfig;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**", "/login/**", "/oauth2/**", "/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestRepository(authorizationRequestRepository())
                    .baseUri("/oauth2/authorization")
                )
                .redirectionEndpoint(redirection -> redirection
                    .baseUri("/login/oauth2/code/*")
                )
                .successHandler(oauth2LoginSuccessHandler())
                .failureHandler(oauth2LoginFailureHandler())
            );

        return http.build();
    }

    @Bean
    public AuthenticationSuccessHandler oauth2LoginSuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
            LoginResponse loginResponse = authService.processKakaoLogin(oAuth2User);
            
            String accessToken = loginResponse.getAccessToken();
            String refreshToken = loginResponse.getRefreshToken();
            
            int accessTokenMaxAge = (int) (accessTokenExpiration / 1000);
            int refreshTokenMaxAge = (int) (refreshTokenExpiration / 1000);
            
            addTokenCookie(response, "accessToken", accessToken, accessTokenMaxAge);
            addTokenCookie(response, "refreshToken", refreshToken, refreshTokenMaxAge);
            
            addPublicCookie(response, "isSignin", "true", accessTokenMaxAge); 
            
            String redirectUri = authorizationRequestRepository.getRedirectUri(request);
            if (redirectUri != null) {
                String redirectUrl = redirectUri + "?success=true";
                response.sendRedirect(redirectUrl);
            } else {
                SuccessResponse<LoginResponse> responseBody = SuccessResponse.of("로그인 성공", loginResponse);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), responseBody);
            }
        };
    }

    @Bean
    public AuthenticationFailureHandler oauth2LoginFailureHandler() {
        return (request, response, exception) -> {
            String error = request.getParameter("error");
            String errorDescription = request.getParameter("error_description");
            
            logger.warn("OAuth2 로그인 실패: error={}, errorDescription={}", error, errorDescription);
            
            String redirectUri = authorizationRequestRepository.getRedirectUri(request);
            if (redirectUri != null) {
                String errorCode = error != null ? error : "LOGIN_FAILED";
                String redirectUrl = redirectUri + "?success=false&error=" + errorCode;
                try {
                    response.sendRedirect(redirectUrl);
                } catch (java.io.IOException e) {
                    logger.error("리다이렉트 실패", e);
                    sendFailureJsonResponse(response, error, errorDescription);
                }
            } else {
                sendFailureJsonResponse(response, error, errorDescription);
            }
        };
    }
    
    private void addTokenCookie(HttpServletResponse response, String name, String value, int maxAge) {
        String secureFlag = cookieSecure ? "; Secure" : "";
        String cookieValue = String.format("%s=%s; Path=/; HttpOnly; Max-Age=%d; SameSite=Lax%s", 
            name, value, maxAge, secureFlag);
        response.addHeader("Set-Cookie", cookieValue);
    }
    
    private void addPublicCookie(HttpServletResponse response, String name, String value, int maxAge) {
        String secureFlag = cookieSecure ? "; Secure" : "";
        String cookieValue = String.format("%s=%s; Path=/; Max-Age=%d; SameSite=Lax%s", 
            name, value, maxAge, secureFlag);
        response.addHeader("Set-Cookie", cookieValue);
    }
    
    private void sendFailureJsonResponse(HttpServletResponse response, String error, String errorDescription) {
        String errorCode = error != null ? error : "LOGIN_FAILED";
        logger.warn("OAuth2 로그인 실패: error={}, errorDescription={}", error, errorDescription);
        ErrorResponse responseBody = ErrorResponse.of(ErrorMessages.LOGIN_FAILED, errorCode);

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        try {
            objectMapper.writeValue(response.getWriter(), responseBody);
        } catch (java.io.IOException e) {
            logger.error("로그인 실패 응답 작성 중 오류 발생", e);
        }
    }

    @Bean
    public CookieOAuth2AuthorizationRequestRepository authorizationRequestRepository() {
        Set<String> allowedRedirectUris = Arrays.stream(allowedRedirectUrisConfig.split(","))
            .map(String::trim)
            .filter(uri -> !uri.isEmpty())
            .collect(Collectors.toSet());
        
        return new CookieOAuth2AuthorizationRequestRepository(
            cookieSecure, 
            oauth2HmacSecret, 
            objectMapper,
            allowedRedirectUris);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:5173"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
