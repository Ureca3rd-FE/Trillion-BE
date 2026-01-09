package com.trillion.server.common.filter;

import java.io.IOException;
import java.util.ArrayList;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.trillion.server.common.util.JwtUtil;
import com.trillion.server.users.entity.UserEntity.UserStatus;
import com.trillion.server.users.repository.UserRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, 
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        String token = extractTokenFromCookie(request);
        
        if (token != null && jwtUtil.validateToken(token, "ACCESS")) {
            try {
                Long userId = jwtUtil.extractUserId(token);
                String role = jwtUtil.extractRole(token);
                
                if (userId != null && role != null) {
                    userRepository.findById(userId).ifPresentOrElse(
                        user -> {
                            if (user.getStatus() == UserStatus.DELETED) {
                                logger.warn("탈퇴한 사용자의 인증 시도: userId={}", userId);
                                SecurityContextHolder.clearContext();
                            } else if (user.getStatus() != UserStatus.ACTIVE) {
                                logger.warn("비활성 사용자의 인증 시도: userId={}, status={}", userId, user.getStatus());
                                SecurityContextHolder.clearContext();
                            } else {
                                Authentication authentication = createAuthentication(userId, role);
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }
                        },
                        () -> {
                            logger.warn("존재하지 않는 사용자의 인증 시도: userId={}", userId);
                            SecurityContextHolder.clearContext();
                        }
                    );
                }
            } catch (Exception e) {
                logger.debug("JWT 인증 필터에서 예외 발생", e);
                SecurityContextHolder.clearContext();
            }
        }
        
        filterChain.doFilter(request, response);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("accessToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    private Authentication createAuthentication(Long userId, String role) {
        var authorities = new ArrayList<SimpleGrantedAuthority>();
        authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
        
        return new UsernamePasswordAuthenticationToken(
            userId,
            null,
            authorities
        );
    }
}
