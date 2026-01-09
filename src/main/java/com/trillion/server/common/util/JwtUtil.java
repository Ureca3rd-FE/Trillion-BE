package com.trillion.server.common.util;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.trillion.server.common.exception.ErrorMessages;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpiration;
    private final long refreshTokenExpiration;

    public JwtUtil(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expiration:3600000}") long accessTokenExpiration,
            @Value("${jwt.refresh-token-expiration:604800000}") long refreshTokenExpiration) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.accessTokenExpiration = accessTokenExpiration;
        this.refreshTokenExpiration = refreshTokenExpiration;
    }

    public String generateAccessToken(Long userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        claims.put("type", "ACCESS");

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public String generateRefreshToken(Long userId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("type", "REFRESH");

        return Jwts.builder()
                .claims(claims)
                .subject(userId.toString())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(secretKey)
                .compact();
    }

    public Claims extractClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (io.jsonwebtoken.security.SecurityException | io.jsonwebtoken.MalformedJwtException | 
                 io.jsonwebtoken.ExpiredJwtException | io.jsonwebtoken.UnsupportedJwtException | 
                 IllegalArgumentException e) {
            throw new IllegalArgumentException(String.format(ErrorMessages.INVALID_TOKEN_WITH_DETAIL, e.getMessage()));
        }
    }

    public Long extractUserId(String token) {
        Claims claims = extractClaims(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(claims.getSubject());
    }

    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        return (String) claims.get("role");
    }

    public String extractTokenType(String token) {
        Claims claims = extractClaims(token);
        return (String) claims.get("type");
    }

    public boolean isTokenExpired(Claims claims) {
        return claims.getExpiration().before(new Date());
    }

    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return isTokenExpired(claims);
        } catch (Exception e) {
            return true;
        }
    }

    public boolean validateToken(String token, String expectedType) {
        try {
            Claims claims = extractClaims(token);
            String type = (String) claims.get("type");
            return type != null && type.equals(expectedType) && !isTokenExpired(claims);
        } catch (Exception e) {
            return false;
        }
    }

    public LocalDateTime getTokenExpirationAsLocalDateTime(String token) {
        try {
            Claims claims = extractClaims(token);
            Date expiration = claims.getExpiration();
            return LocalDateTime.ofInstant(expiration.toInstant(), ZoneId.systemDefault());
        } catch (Exception e) {
            throw new IllegalArgumentException("토큰 만료 시간을 가져올 수 없습니다: " + e.getMessage());
        }
    }
}
