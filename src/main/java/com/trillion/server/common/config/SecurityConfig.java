package com.trillion.server.common.config;

import java.nio.charset.StandardCharsets;
import java.util.*;

import com.trillion.server.auth.dto.AuthDto;
import com.trillion.server.auth.filter.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trillion.server.auth.service.AuthService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.util.UriComponentsBuilder;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final AuthService authService;
    private final ObjectMapper objectMapper;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthorizedClientRepository authorizedClientRepository;
    
    @Value("${jwt.access-token-expiration:3600000}")
    private long accessTokenExpiration;
    
    @Value("${jwt.refresh-token-expiration:604800000}")
    private long refreshTokenExpiration;

    @Value("${app.cookie.secure}")
    private boolean cookieSecure;

    @Value("${app.oauth2.hmac-secret}")
    private String cookieHmacKey;

    @Value("#{'${app.oauth2.allowed-redirect-uris}'.split(',')}")
    private Set<String> allowedRedirectUris;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session
                .sessionCreationPolicy(org.springframework.security.config.http.SessionCreationPolicy.STATELESS)
            )
                .headers(headers -> headers
                .cacheControl(cache -> cache.disable()))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/refresh", "/login/**", "/oauth2/**", "/error").permitAll()
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/swagger-resources/**", "/webjars/**").permitAll()
                .anyRequest().authenticated()
            )

            .exceptionHandling(exception -> exception
                    .authenticationEntryPoint((request, response, authException) -> {
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding("UTF-8");
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);

                        Map<String, Object> body = new HashMap<>();
                        body.put("success", false);
                        body.put("message", "인증되지 않은 사용자입니다.");
                        body.put("error", "UNAUTHORIZED");

                        objectMapper.writeValue(response.getWriter(), body);
                    })
            )

            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .oauth2Login(oauth2 -> oauth2
                .authorizationEndpoint(authorization -> authorization
                    .authorizationRequestRepository(cookieAuthorizationRequestRepository())
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
    public CookieOAuth2AuthorizationRequestRepository cookieAuthorizationRequestRepository() {
        return new CookieOAuth2AuthorizationRequestRepository(
                cookieSecure,
                cookieHmacKey,
                objectMapper,
                allowedRedirectUris
        );
    }

    @Bean
    public AuthenticationSuccessHandler oauth2LoginSuccessHandler() {
        return (request, response, authentication) -> {
            OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();

            String kakaoRefreshToken = null;
            if(authentication instanceof OAuth2AuthenticationToken oauthToken){
                OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient(
                        oauthToken.getAuthorizedClientRegistrationId(),
                        oauthToken,
                        request
                );

                if(client != null && client.getRefreshToken() != null){
                    kakaoRefreshToken = client.getRefreshToken().getTokenValue();
                }
            }
            AuthDto.LoginResponse loginResponse = authService.processKakaoLogin(oAuth2User, kakaoRefreshToken);
            
            String accessToken = loginResponse.accessToken();
            String refreshToken = loginResponse.refreshToken();

            int accessTokenMaxAge = (int) (accessTokenExpiration / 1000);
            int refreshTokenMaxAge = (int) (refreshTokenExpiration / 1000);
            
            addTokenCookie(response, "accessToken", accessToken, accessTokenMaxAge);
            addTokenCookie(response, "refreshToken", refreshToken, refreshTokenMaxAge);

            String targetUrl;
            String frontUrl ="http://localhost:3000";

            if(loginResponse.isNewUser()){
                targetUrl = UriComponentsBuilder.fromUriString(frontUrl + "/auth/logincheck")
                        .queryParam("isNewUser", true)
                        .build().toUriString();
            }else{
                targetUrl = UriComponentsBuilder.fromUriString(frontUrl + "/")
                        .build().toUriString();
            }
            response.sendRedirect(targetUrl);
        };
    }

    @Bean
    public AuthenticationFailureHandler oauth2LoginFailureHandler() {
        return (request, response, exception) -> {
            String error = request.getParameter("error");
            String errorDescription = request.getParameter("error_description");
            
            String redirectUri = getRedirectUriFromCookie(request);
            
            if (redirectUri != null && !redirectUri.isBlank()) {
                String redirectUrl = redirectUri + 
                    "?success=false" +
                    "&message=" + java.net.URLEncoder.encode("로그인에 실패했습니다.", java.nio.charset.StandardCharsets.UTF_8);
                
                if (error != null) {
                    redirectUrl += "&error=" + java.net.URLEncoder.encode(error, java.nio.charset.StandardCharsets.UTF_8);
                }
                if (errorDescription != null) {
                    redirectUrl += "&errorDescription=" + java.net.URLEncoder.encode(errorDescription, java.nio.charset.StandardCharsets.UTF_8);
                }
                
                try {
                    response.sendRedirect(redirectUrl);
                } catch (java.io.IOException e) {
                    sendFailureJsonResponse(response, error, errorDescription);
                }
            } else {
                sendFailureJsonResponse(response, error, errorDescription);
            }
        };
    }
    
    private String getRedirectUriFromCookie(HttpServletRequest request) {
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("redirect_uri".equals(cookie.getName())) {
                    String encodedUri = cookie.getValue();
                    try {
                        byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedUri);
                        return new String(decodedBytes, StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private void addTokenCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .path("/")
                .sameSite("Lax")
                .httpOnly(true)
                .secure(cookieSecure)
                .maxAge(maxAge)
                .build();

        response.addHeader("Set-Cookie", cookie.toString());
    }
    
    private void sendFailureJsonResponse(HttpServletResponse response, String error, String errorDescription) {
        Map<String, Object> responseBody = new HashMap<>();
        responseBody.put("success", false);
        responseBody.put("message", "로그인에 실패했습니다.");
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
            e.printStackTrace();
        }
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
