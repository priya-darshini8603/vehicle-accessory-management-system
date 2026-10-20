package org.example.service;

import org.example.entity.User;

public interface RefreshTokenService {

    // Issues a refresh token for a fresh login (full lifetime)
    String createFor(User user);

    // Consumes the given token and issues a replacement that keeps the original absolute expiry
    RotatedToken rotate(String rawToken);

    // Idempotently revokes the token. Unknown, expired or already revoked tokens are ignored
    void revoke(String rawToken);

    record RotatedToken(User user, String refreshToken) {}
}