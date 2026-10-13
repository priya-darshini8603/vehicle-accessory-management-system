
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
     * Configures stateless JWT security for the API layer.
     * CSRF is ignored only for authentication endpoints because they are caller-driven,
     * while the rest of the application still keeps the default protection.
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
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(HttpMethod.POST,
                                    "/api/auth/register",
                                    "/api/auth/login")
                            .permitAll()
                            .requestMatchers("/error").permitAll()

                            // Only administrators can manage system inventory.
                            .requestMatchers("/api/admin/**").hasRole("ADMIN")
                            .requestMatchers("/api/accessories/**").hasRole("ADMIN")

                            // Regular users must authenticate before accessing their section.
                            .requestMatchers("/api/user/**").authenticated()
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
