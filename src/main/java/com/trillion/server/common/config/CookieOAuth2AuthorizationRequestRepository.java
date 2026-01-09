package com.trillion.server.common.config;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class CookieOAuth2AuthorizationRequestRepository implements AuthorizationRequestRepository<OAuth2AuthorizationRequest> {

    private static final Logger logger = LoggerFactory.getLogger(CookieOAuth2AuthorizationRequestRepository.class);
    private static final String OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME = "oauth2_auth_request";
    private static final String REDIRECT_URI_PARAM_COOKIE_NAME = "redirect_uri";
    private static final int COOKIE_EXPIRE_SECONDS = 180;
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String SIGNATURE_SEPARATOR = ".";

    private final boolean cookieSecure;
    private final String hmacSecret;
    private final ObjectMapper objectMapper;

    public CookieOAuth2AuthorizationRequestRepository(boolean cookieSecure, String hmacSecret, ObjectMapper objectMapper) {
        this.cookieSecure = cookieSecure;
        this.hmacSecret = hmacSecret;
        this.objectMapper = objectMapper;
    }

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        Cookie cookie = getCookie(request, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        if (cookie != null) {
            return deserialize(cookie);
        }
        return null;
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest authorizationRequest, 
            HttpServletRequest request, HttpServletResponse response) {
        if (authorizationRequest == null) {
            deleteCookies(request, response);
            return;
        }

        String serializedRequest = serialize(authorizationRequest);
        if (serializedRequest != null) {
            addCookie(response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME, 
                serializedRequest, COOKIE_EXPIRE_SECONDS);
        }
        
        String redirectUriAfterLogin = request.getParameter(REDIRECT_URI_PARAM_COOKIE_NAME);
        if (redirectUriAfterLogin != null && !redirectUriAfterLogin.isBlank()) {
            addCookie(response, REDIRECT_URI_PARAM_COOKIE_NAME, redirectUriAfterLogin, COOKIE_EXPIRE_SECONDS);
        }
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request, 
            HttpServletResponse response) {
        OAuth2AuthorizationRequest authRequest = this.loadAuthorizationRequest(request);
        if (authRequest != null) {
            deleteCookies(request, response);
        }
        return authRequest;
    }

    private void deleteCookies(HttpServletRequest request, HttpServletResponse response) {
        deleteCookie(request, response, OAUTH2_AUTHORIZATION_REQUEST_COOKIE_NAME);
        deleteCookie(request, response, REDIRECT_URI_PARAM_COOKIE_NAME);
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(maxAge);
        cookie.setSecure(cookieSecure);
        response.addCookie(cookie);
    }

    private void deleteCookie(HttpServletRequest request, HttpServletResponse response, String name) {
        Cookie cookie = findCookie(request, name);
        if (cookie != null) {
            cookie.setValue("");
            cookie.setPath("/");
            cookie.setMaxAge(0);
            cookie.setHttpOnly(true);
            cookie.setSecure(cookieSecure);
            response.addCookie(cookie);
        }
    }

    private Cookie getCookie(HttpServletRequest request, String name) {
        return findCookie(request, name);
    }

    private Cookie findCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookie.getName().equals(name)) {
                    return cookie;
                }
            }
        }
        return null;
    }

    private String serialize(OAuth2AuthorizationRequest request) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("authorizationUri", request.getAuthorizationUri());
            data.put("clientId", request.getClientId());
            data.put("redirectUri", request.getRedirectUri());
            if (request.getScopes() != null) {
                data.put("scopes", request.getScopes());
            }
            data.put("state", request.getState());
            if (request.getAdditionalParameters() != null && !request.getAdditionalParameters().isEmpty()) {
                data.put("additionalParameters", request.getAdditionalParameters());
            }
            if (request.getAuthorizationRequestUri() != null) {
                data.put("authorizationRequestUri", request.getAuthorizationRequestUri());
            }
            if (request.getAttributes() != null && !request.getAttributes().isEmpty()) {
                data.put("attributes", request.getAttributes());
            }

            String json = objectMapper.writeValueAsString(data);
            String signature = calculateHmac(json);
            String signedData = json + SIGNATURE_SEPARATOR + signature;
            
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                signedData.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            logger.error("OAuth2AuthorizationRequest 직렬화 실패", e);
            return null;
        }
    }

    private OAuth2AuthorizationRequest deserialize(Cookie cookie) {
        try {
            String signedData = new String(
                Base64.getUrlDecoder().decode(cookie.getValue()), 
                StandardCharsets.UTF_8);
            
            int separatorIndex = signedData.lastIndexOf(SIGNATURE_SEPARATOR);
            if (separatorIndex == -1) {
                logger.warn("서명이 없는 쿠키 데이터");
                return null;
            }

            String json = signedData.substring(0, separatorIndex);
            String signature = signedData.substring(separatorIndex + 1);

            if (!verifyHmac(json, signature)) {
                logger.warn("쿠키 서명 검증 실패");
                return null;
            }

            Map<String, Object> data = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            
            OAuth2AuthorizationRequest.Builder builder = OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri((String) data.get("authorizationUri"))
                .clientId((String) data.get("clientId"))
                .redirectUri((String) data.get("redirectUri"))
                .state((String) data.get("state"));
            
            if (data.containsKey("scopes")) {
                Object scopesObj = data.get("scopes");
                if (scopesObj != null) {
                    java.util.Set<String> scopes;
                    if (scopesObj instanceof java.util.Set) {
                        @SuppressWarnings("unchecked")
                        java.util.Set<String> setScopes = (java.util.Set<String>) scopesObj;
                        scopes = setScopes;
                    } else if (scopesObj instanceof java.util.List) {
                        @SuppressWarnings("unchecked")
                        java.util.List<String> listScopes = (java.util.List<String>) scopesObj;
                        scopes = new java.util.HashSet<>(listScopes);
                    } else {
                        logger.warn("예상치 못한 scopes 타입: {}", scopesObj.getClass());
                        scopes = java.util.Collections.emptySet();
                    }
                    builder.scopes(scopes);
                }
            }
            
            if (data.containsKey("additionalParameters")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> additionalParameters = (Map<String, Object>) data.get("additionalParameters");
                if (additionalParameters != null && !additionalParameters.isEmpty()) {
                    builder.additionalParameters(additionalParameters);
                }
            }
            
            if (data.containsKey("authorizationRequestUri")) {
                builder.authorizationRequestUri((String) data.get("authorizationRequestUri"));
            }
            
            if (data.containsKey("attributes")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> attributes = (Map<String, Object>) data.get("attributes");
                if (attributes != null && !attributes.isEmpty()) {
                    builder.attributes(attributes);
                }
            }
            
            return builder.build();
        } catch (Exception e) {
            logger.error("OAuth2AuthorizationRequest 역직렬화 실패", e);
            return null;
        }
    }

    private String calculateHmac(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                hmacSecret.getBytes(StandardCharsets.UTF_8), 
                HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hmacBytes);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            logger.error("HMAC 계산 실패", e);
            throw new RuntimeException("HMAC 계산 실패", e);
        }
    }

    private boolean verifyHmac(String data, String signature) {
        String calculatedSignature = calculateHmac(data);
        return calculatedSignature.equals(signature);
    }
}
