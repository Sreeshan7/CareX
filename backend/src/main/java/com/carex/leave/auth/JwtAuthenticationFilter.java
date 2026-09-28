package com.carex.leave.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** Bearer token → CurrentUser principal. Invalid/expired tokens simply leave the request unauthenticated (→ 401). */
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwt;
    private final UserDirectory directory;

    public JwtAuthenticationFilter(JwtService jwt, UserDirectory directory) {
        this.jwt = jwt;
        this.directory = directory;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            jwt.parseUserId(header.substring(7).trim())
                    .flatMap(directory::load)
                    .ifPresent(user -> {
                        var auth = new UsernamePasswordAuthenticationToken(user, null, user.role().authorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                        MDC.put("userId", String.valueOf(user.id()));
                    });
        }
        chain.doFilter(request, response);
    }
}
