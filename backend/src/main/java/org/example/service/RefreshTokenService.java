package org.example.service;

import org.example.entity.User;

public interface RefreshTokenService {

    String createFor(User user);

    RotatedToken rotate(String rawToken);

    void revoke(String rawToken, String email);

    record RotatedToken(User user, String refreshToken) {}
}
