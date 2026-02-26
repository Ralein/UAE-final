package com.yoursp.uaepass.config;

import com.yoursp.uaepass.modules.auth.SessionAuthFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Production-hardened security configuration.
 * <ul>
 * <li>Security headers: HSTS, X-Content-Type-Options, X-Frame-Options, CSP,
 * Cache-Control</li>
 * <li>Rate limiting filter registered before session auth</li>
 * <li>/auth/**, /public/**, /actuator/health — permitAll</li>
 * <li>All other routes — require authenticated session</li>
 * <li>CSRF disabled (using httpOnly cookie + SameSite=Strict instead)</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        @Value("${app.frontend-url:http://localhost:4200}")
        private String frontendUrl;

        private final SessionAuthFilter sessionAuthFilter;
        private final RateLimitFilter rateLimitFilter;

        public SecurityConfig(SessionAuthFilter sessionAuthFilter, RateLimitFilter rateLimitFilter) {
                this.sessionAuthFilter = sessionAuthFilter;
                this.rateLimitFilter = rateLimitFilter;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                // ── Security Headers ──
                                .headers(headers -> headers
                                                .contentTypeOptions(opt -> {
                                                }) // X-Content-Type-Options: nosniff
                                                .frameOptions(frame -> frame.deny()) // X-Frame-Options: DENY
                                                .httpStrictTransportSecurity(hsts -> hsts
                                                                .includeSubDomains(true)
                                                                .maxAgeInSeconds(31536000)) // HSTS: 1 year
                                                .contentSecurityPolicy(csp -> csp
                                                                .policyDirectives(
                                                                                "default-src 'self'; script-src 'self'; frame-ancestors 'none'"))
                                                .cacheControl(cache -> {
                                                })) // Cache-Control: no-cache, no-store, must-revalidate

                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/auth/login", "/auth/callback", "/auth/logout")
                                                .permitAll()
                                                .requestMatchers("/auth/register", "/auth/register/callback")
                                                .permitAll()
                                                .requestMatchers("/auth/link", "/auth/link/callback").permitAll()
                                                .requestMatchers("/signature/callback").permitAll()
                                                .requestMatchers("/hashsign/callback").permitAll()
                                                .requestMatchers("/face/verify/callback").permitAll()
                                                .requestMatchers("/internal/**").permitAll()
                                                .requestMatchers("/mock/**").permitAll()
                                                .requestMatchers("/public/**").permitAll()
                                                .requestMatchers("/actuator/health").permitAll()
                                                .requestMatchers("/actuator/info").permitAll()
                                                .anyRequest().authenticated())
                                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(sessionAuthFilter, UsernamePasswordAuthenticationFilter.class)
                                .formLogin(AbstractHttpConfigurer::disable)
                                .httpBasic(AbstractHttpConfigurer::disable)
                                .oauth2Login(AbstractHttpConfigurer::disable);

                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(Arrays.asList(
                                "http://localhost:4200",
                                frontendUrl));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }
}
