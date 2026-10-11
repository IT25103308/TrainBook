package com.trainbooking.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security configuration class for defining authentication and authorization rules,
 * form login behavior, CSRF policies, and password encoding beans.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public org.springframework.security.core.session.SessionRegistry sessionRegistry() {
        return new org.springframework.security.core.session.SessionRegistryImpl();
    }

    @Bean
    public org.springframework.security.web.session.HttpSessionEventPublisher sessionEvents() {
        return new org.springframework.security.web.session.HttpSessionEventPublisher();
    }

    /**
     * Configures the main HTTP security filter chain.
     *
     * @param http the {@link HttpSecurity} to modify
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception in case of configuration errors
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Keep Spring's default GET-only cache (CSRF is enabled), which excludes
            // favicon, AJAX and non-page requests from the post-login destination.
            .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessionRegistry()).expiredUrl("/login?sessionExpired=true"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(org.springframework.http.HttpMethod.GET, "/", "/trains/search", "/booking/seats", "/departure-board",
                        "/notifications/map", "/notifications/live-map", "/trains/live-map",
                        "/api/trains/locations", "/api/trains/*/location", "/api/trains/live-status").permitAll()
                .requestMatchers("/login", "/register", "/terms", "/privacy", "/forgot-password", "/reset-password", "/access-denied",
                        "/css/**", "/js/**", "/images/**", "/favicon.ico", "/error").permitAll()
                .requestMatchers("/admin/accounts","/admin/accounts/**").hasRole("ADMIN")
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/dashboard/boarding-logs/**").hasRole("ADMIN")
                .requestMatchers("/validate/*/reset", "/dashboard/validate/*/reset").hasRole("ADMIN")
                .requestMatchers("/validate", "/validate/**", "/dashboard/validate", "/dashboard/validate/**",
                        "/dashboard/boarding-logs", "/dashboard/boarding-logs/**", "/api/tickets/validate").hasAnyRole("ADMIN", "STATION_STAFF")
                .requestMatchers("/dashboard", "/dashboard/**", "/api/dashboard/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                .requestMatchers("/trains", "/trains/**", "/schedules", "/schedules/**", "/admin/**", "/notifications/status", "/notifications/status/**",
                        "/api/trains/location/update", "/api/trains/*/location/clear", "/notifications/telemetry", "/notifications/telemetry/**").hasAnyRole("ADMIN", "COORDINATOR")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, exception) -> {
                response.setStatus(403);
                if (request.getRequestURI().startsWith(request.getContextPath() + "/api/")) {
                    response.setContentType("application/json");
                    response.getWriter().write("{\"success\":false,\"error\":\"Access restricted for this account.\"}");
                } else {
                    request.getRequestDispatcher("/access-denied").forward(request, response);
                }
            }))
            .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", false).permitAll())
            .logout(logout -> logout.logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll());

        return http.build();
    }

    /**
     * Declares the BCrypt password encoder bean.
     *
     * @return {@link PasswordEncoder} instance
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
