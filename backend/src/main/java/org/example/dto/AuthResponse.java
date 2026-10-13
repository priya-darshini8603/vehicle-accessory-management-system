
package org.example.dto;

/**
 * Authentication payload returned after a successful login.
 * The token is used by the client for subsequent protected API calls.
 */
public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserResponse user
) {}
