
package org.example.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        log.debug("Creating password encoder");
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration) {

        try {
            log.debug("Initializing authentication manager");
            return configuration.getAuthenticationManager();
        } catch (Exception exception) {
            log.error("Authentication manager initialization failed",
                    exception);
            throw new IllegalStateException(
                    "Authentication manager could not be initialized",
                    exception);
        }
    }

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
                            .requestMatchers(
                                    HttpMethod.POST,
                                    "/api/auth/register",
                                    "/api/auth/login"
                            ).permitAll()
                            .requestMatchers("/error").permitAll()

                            // Only admins can manage inventory.
                            .requestMatchers("/api/admin/**")
                            .hasRole("ADMIN")
                            .requestMatchers("/api/accessories/**")
                            .hasRole("ADMIN")

                            // Authenticated users can access their section.
                            .requestMatchers("/api/user/**")
                            .authenticated()

                            // Includes POST /api/auth/logout.
                            .anyRequest().authenticated()
                    )
                    .headers(Customizer.withDefaults())
                    .addFilterBefore(
                            jwtFilter,
                            UsernamePasswordAuthenticationFilter.class
                    );

            SecurityFilterChain filterChain = http.build();

            log.info("Security filter chain initialized");
            return filterChain;

        } catch (Exception exception) {
            log.error("Security filter chain initialization failed",
                    exception);
            throw new IllegalStateException(
                    "Security filter chain initialization failed",
                    exception);
        }
    }
}
