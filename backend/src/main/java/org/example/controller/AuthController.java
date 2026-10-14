
package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.dto.AuthResponse;
import org.example.dto.LoginRequest;
import org.example.dto.RegisterRequest;
import org.example.dto.UserResponse;
import org.example.security.RevokedTokenService;
import org.example.service.AuthService;
import org.example.service.JwtService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final RevokedTokenService revokedTokenService;
    private final JwtService jwtService;

    // Register a new user.
    @PostMapping("/register")
    @PreAuthorize("permitAll()")
    public ResponseEntity<UserResponse> register(
            @Valid @RequestBody RegisterRequest request) {

        log.debug("Received user registration request");

        UserResponse response = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // Log in and return a JWT.
    @PostMapping("/login")
    @PreAuthorize("permitAll()")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request) {

        log.debug("Received user login request");

        return ResponseEntity.ok(authService.login(request));
    }

    // Revoke the current JWT.
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            HttpServletRequest request) {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "MISSING_TOKEN",
                            "message", "Bearer token is required"));
        }

        String token = header.substring(7).trim();

        if (token.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "error", "MISSING_TOKEN",
                            "message", "Bearer token is required"));
        }

        // The JWT filter validates the token before this controller runs.
        long expirationTimeMillis =
                jwtService.extractExpiration(token).getTime();

        revokedTokenService.revokeToken(token, expirationTimeMillis);

        log.info("User logged out successfully");

        return ResponseEntity.ok(
                Map.of("message", "Logout successful"));
    }
}
