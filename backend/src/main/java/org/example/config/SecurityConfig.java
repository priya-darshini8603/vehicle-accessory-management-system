
package org.example.config;

import lombok.RequiredArgsConstructor;
import org.example.security.JwtAuthenticationFilter;
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
     * //JWT filter that validates each incoming bearer token before the request continues.
     */
    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) {
        try {
            return configuration.getAuthenticationManager();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Authentication manager could not be initialized",
                    exception
            );
        }
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        try {
            http
                    .csrf(AbstractHttpConfigurer::disable)
                    .httpBasic(AbstractHttpConfigurer::disable)
                    .formLogin(AbstractHttpConfigurer::disable)

                    // Do not create or use an HTTP session for authentication.
                    .sessionManagement(session -> session
                            .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                    .authorizeHttpRequests(auth -> auth
                            // Public authentication endpoints.
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/auth/register",
                                    "/api/auth/login"
                            ).permitAll()

                            .requestMatchers("/error").permitAll()

                            // Only administrators can manage inventory.
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/accessories/**").hasRole("ADMIN")

                            // Authenticated users can access their own section.
                            .requestMatchers("/api/user/**").authenticated()

                            // All other endpoints require authentication.
                            .anyRequest().authenticated()
                    )

                    .headers(Customizer.withDefaults())

                    .addFilterBefore(
                            jwtFilter,
                            UsernamePasswordAuthenticationFilter.class
                    );

            return http.build();

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Security filter chain initialization failed",
                    exception
            );
        }
    }
}
