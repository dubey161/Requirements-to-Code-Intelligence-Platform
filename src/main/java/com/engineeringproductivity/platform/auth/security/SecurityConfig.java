package com.engineeringproductivity.platform.auth.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.engineeringproductivity.platform.auth.application.JwtProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security configuration.
 *
 * Role hierarchy:
 *   ADMIN              → everything
 *   ENGINEERING_MANAGER → core platform + manager dashboard
 *   DEVELOPER          → core platform + developer dashboard
 *
 * Public routes (no token required):
 *   POST /auth/**            — register, login, refresh
 *   POST /webhook/github     — GitHub webhook
 *   GET  /actuator/health    — health probe
 *   GET  /actuator/info      — build info
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth

                        // ── Public ────────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/auth/register", "/auth/login", "/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/webhook/github").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // ── Admin only ────────────────────────────────────────
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/dashboard/admin").hasRole("ADMIN")

                        // ── Manager and above ─────────────────────────────────
                        .requestMatchers("/api/v1/dashboard/manager").hasAnyRole("ADMIN", "ENGINEERING_MANAGER")

                        // ── Any authenticated user ────────────────────────────
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
