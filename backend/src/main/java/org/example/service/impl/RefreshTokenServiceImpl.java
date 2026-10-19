package org.example.service.impl;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.RefreshToken;
import org.example.entity.User;
import org.example.exception.DuplicateResourceException;
import org.example.exception.InvalidRefreshTokenException;
import org.example.exception.RefreshTokenOwnershipException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.RefreshTokenRepository;
import org.example.security.TokenUtils;
import org.example.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${app.refresh-token.expiration-ms}")
    private long expirationMs;

    @PostConstruct
    void validateExpiration() {
        if (expirationMs <= 0) {
            throw new IllegalStateException("app.refresh-token.expiration-ms must be positive");
        }
        log.info("Refresh token service initialized with expiration of {} ms", expirationMs);
    }

    @Override
    public String createFor(User user) {
        String rawToken = TokenUtils.generateToken();
        RefreshToken token = new RefreshToken();
        // Persist only a hash so a database read cannot reveal a usable refresh token.
        token.setTokenHash(TokenUtils.hash(rawToken));
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusMillis(expirationMs));
        refreshTokenRepository.save(token);
        log.debug("Created refresh token for user id {}", user.getId());
        return rawToken;
    }

    @Override
    @Transactional
    public RotatedToken rotate(String rawToken) {
        Instant now = Instant.now();
        String tokenHash = TokenUtils.hash(rawToken);
        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token rotation rejected because the token was not found");
                    return new InvalidRefreshTokenException();
                });
        if (current.getRevokedAt() != null || !current.getExpiresAt().isAfter(now)) {
            log.warn("Refresh token rotation rejected for user id {} because it is revoked or expired",
                    current.getUser().getId());
            throw new InvalidRefreshTokenException();
        }

        // Conditional update makes rotation single-use even when concurrent requests race.
        if (refreshTokenRepository.revokeIfActive(tokenHash, now) != 1) {
            log.warn("Refresh token rotation rejected for user id {} because it was already consumed",
                    current.getUser().getId());
            throw new InvalidRefreshTokenException();
        }

        User user = current.getUser();
        String replacement = createFor(user);
        log.info("Rotated refresh token for user id {}", user.getId());
        return new RotatedToken(user, replacement);
    }

    @Override
    @Transactional
    public void revoke(String rawToken, String email) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException("Refresh token is required");
        }

        String tokenHash = TokenUtils.hash(rawToken);

        // 404: hash not found (covers random and non-existent tokens)
        RefreshToken token = refreshTokenRepository.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found"));

        // 403: belongs to another user (checked first so another user's token state is never leaked)
        if (!token.getUser().getEmail().equalsIgnoreCase(email)) {
            log.warn("Logout rejected: refresh token does not belong to the authenticated user");
            throw new RefreshTokenOwnershipException();
        }

        // 409: already revoked
        if (token.getRevokedAt() != null) {
            throw new DuplicateResourceException("Refresh token already revoked");
        }

        // 401: expired
        Instant now = Instant.now();
        if (!token.getExpiresAt().isAfter(now)) {
            throw new InvalidRefreshTokenException("Refresh token expired");
        }

        // Revoke only after every check has passed; the conditional update guards against races.
        if (refreshTokenRepository.revokeIfActiveForUser(
                tokenHash, token.getUser().getId(), now) != 1) {
            throw new DuplicateResourceException("Refresh token already revoked");
        }
    }
}