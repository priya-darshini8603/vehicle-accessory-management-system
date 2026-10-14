
package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.security.JwtAuthenticationFilter;
import org.example.security.RestAccessDeniedHandler;
import org.example.security.RestAuthenticationEntryPoint;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    /**
     * JWT filter that validates each incoming bearer token before the request continues.
     */
    private final JwtAuthenticationFilter jwtFilter;

    /**
     * Produce the standard JSON error body for 401 and 403 responses.
     */
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * Password hashing strategy used for user registration and authentication.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /**
     * Builds the Spring AuthenticationManager used by the login flow.
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) {
        try {
            return configuration.getAuthenticationManager();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Authentication manager could not be initialized", exception);
        }
    }

    /**
     * The JWT filter is a @Component, so Spring Boot would also register it as a plain servlet filter.
     * It must only run inside the security filter chain.
     */
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilterRegistration() {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(jwtFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * Configures stateless JWT security for the API layer.
     * CSRF protection is disabled because the API uses bearer tokens, not cookies.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint(authenticationEntryPoint)
                            .accessDeniedHandler(accessDeniedHandler))
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST,
                                    "/api/auth/register",
                                    "/api/auth/login")
                            .permitAll()
                            .requestMatchers("/error").permitAll()

                            // Only administrators can manage vehicles, accessories, mappings and users.
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")

                            // Compatibility search is open to every signed-in role.
                            .requestMatchers(HttpMethod.GET, "/api/compatibility/**", "/api/compatibility")
                            .hasAnyRole("ADMIN", "CUSTOMER")

                            .anyRequest().authenticated())
                    .headers(Customizer.withDefaults())
                    .addFilterBefore(
                            jwtFilter,
                            UsernamePasswordAuthenticationFilter.class);

            return http.build();
        } catch (Exception exception) {
            throw new IllegalStateException("Security filter chain initialization failed", exception);
        }
    }
}
