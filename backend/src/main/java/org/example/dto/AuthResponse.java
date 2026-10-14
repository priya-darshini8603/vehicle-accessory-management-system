
package org.example.dto;

// Data returned after a successful login.
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {}
