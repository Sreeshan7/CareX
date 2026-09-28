package com.carex.leave.config;

import com.carex.leave.auth.JwtAuthenticationFilter;
import com.carex.leave.auth.JwtService;
import com.carex.leave.auth.UserDirectory;
import com.carex.leave.common.error.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.List;

/** implementation.md §13, §14.2 (route layer), §19.2 (headers). Object-level checks live in AccessPolicy. */
@Configuration
public class SecurityConfig {

    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
            "font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: blob:; media-src 'self' blob: data:; " +
            "connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, JwtService jwt, UserDirectory directory,
                                           ObjectMapper mapper, AppProperties props) throws Exception {
        http
            .csrf(c -> c.disable()) // Bearer tokens in headers, no cookies → no CSRF surface
            .cors(Customizer.withDefaults())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .headers(h -> h
                .contentSecurityPolicy(csp -> csp.policyDirectives(CSP))
                .frameOptions(f -> f.deny())
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
                .permissionsPolicyHeader(p -> p.policy("microphone=(self), camera=(), geolocation=()")))
            .authorizeHttpRequests(a -> a
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/demo-login", "/api/v1/auth/demo-accounts").permitAll()
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/api/v1/manager/**").hasRole("MANAGER")
                .requestMatchers("/api/v1/hr/**").hasRole("HR")
                .requestMatchers("/api/**").authenticated()
                .requestMatchers("/actuator/**").denyAll()
                .requestMatchers(HttpMethod.GET, "/**").permitAll() // SPA static assets + client routes
                .anyRequest().denyAll())
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> problem(res, mapper, HttpStatus.UNAUTHORIZED,
                        "UNAUTHENTICATED", "Authentication required"))
                .accessDeniedHandler((req, res, ex) -> problem(res, mapper, HttpStatus.FORBIDDEN,
                        "FORBIDDEN", "You are not allowed to do this")))
            .addFilterBefore(new JwtAuthenticationFilter(jwt, directory), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Same-origin deployment → no origins allowed unless explicitly configured (split deployments only). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties props) {
        CorsConfiguration cfg = new CorsConfiguration();
        List<String> origins = props.cors() == null || props.cors().allowedOrigins() == null ? List.of()
                : props.cors().allowedOrigins().stream().filter(o -> !o.isBlank()).toList();
        cfg.setAllowedOrigins(origins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Request-Id", "X-Client-Channel", "Accept-Language"));
        cfg.setExposedHeaders(List.of("X-Request-Id", "Idempotent-Replay"));
        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/api/**", cfg);
        return src;
    }

    private static void problem(HttpServletResponse res, ObjectMapper mapper, HttpStatus status, String code,
                                String detail) throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(res.getOutputStream(), GlobalExceptionHandler.base(status, code, detail));
    }
}
