package com.trillion.server.common.config;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trillion.server.auth.service.AuthService;
import com.trillion.server.common.exception.ErrorMessages;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthService authService;
    private final ObjectMapper objectMapper;
    
    @Value("${jwt.access-token-expiration:3600000}")
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            )
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
            Map<String, Object> result = authService.processKakaoLogin(oAuth2User);
            
            String accessToken = (String) result.get("accessToken");
            String refreshToken = (String) result.get("refreshToken");
            
            int accessTokenMaxAge = (int) (accessTokenExpiration / 1000);
            int refreshTokenMaxAge = (int) (refreshTokenExpiration / 1000);
            
            addTokenCookie(response, "accessToken", accessToken, accessTokenMaxAge);
            addTokenCookie(response, "refreshToken", refreshToken, refreshTokenMaxAge);
            
            addPublicCookie(response, "isSignin", "true", accessTokenMaxAge); 
            
            Map<String, Object> responseBody = new HashMap<>();
            responseBody.put("success", true);
            responseBody.put("message", "로그인 성공");
            responseBody.putAll(result);

            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            objectMapper.writeValue(response.getWriter(), responseBody);
        };
    }

    @Bean
    public AuthenticationFailureHandler oauth2LoginFailureHandler() {
        return (request, response, exception) -> {
            String error = request.getParameter("error");
            String errorDescription = request.getParameter("error_description");
            
            sendFailureJsonResponse(response, error, errorDescription);
        };
    }
    
    private void addTokenCookie(HttpServletResponse response, String name, String value, int maxAge) {
        String cookieValue = String.format("%s=%s; Path=/; HttpOnly; Max-Age=%d; SameSite=Lax", 
            name, value, maxAge);
        response.addHeader("Set-Cookie", cookieValue);
    }
    
    private void addPublicCookie(HttpServletResponse response, String name, String value, int maxAge) {
        String cookieValue = String.format("%s=%s; Path=/; Max-Age=%d; SameSite=Lax", 
            name, value, maxAge);
        response.addHeader("Set-Cookie", cookieValue);
    }
    
    private void sendFailureJsonResponse(HttpServletResponse response, String error, String errorDescription) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", false);
        responseBody.put("message", ErrorMessages.LOGIN_FAILED);
        if (error != null) {
            responseBody.put("error", error);
        }
        if (errorDescription != null) {
            responseBody.put("errorDescription", errorDescription);
        }

        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        try {
            objectMapper.writeValue(response.getWriter(), responseBody);
        } catch (java.io.IOException e) {
            System.err.println("로그인 실패 응답 작성 중 오류 발생: " + e.getMessage());
        }
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
        return new CookieOAuth2AuthorizationRequestRepository();
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
