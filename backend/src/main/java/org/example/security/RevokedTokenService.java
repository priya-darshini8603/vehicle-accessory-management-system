
package org.example.security;

import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RevokedTokenService {

    private final ConcurrentMap<String, Long> revokedTokens =
            new ConcurrentHashMap<>();

    public void revokeToken(String token, long expirationTimeMillis) {
        revokedTokens.put(token, expirationTimeMillis);
    }

    public boolean isTokenRevoked(String token) {
        Long expiration = revokedTokens.get(token);

        if (expiration == null) {
            return false;
        }

        if (System.currentTimeMillis() >= expiration) {
            revokedTokens.remove(token, expiration);
            return false;
        }

        return true;
    }
}
