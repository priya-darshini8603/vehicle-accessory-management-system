package org.example.service.impl;

import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.example.entity.RefreshToken;
import org.example.entity.User;
import org.example.exception.InvalidRefreshTokenException;
import org.example.repository.RefreshTokenRepository;
import org.example.security.TokenUtils;
import org.example.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    // Session lifetime
    private final long expirationMs;

    // Retry grace period after rotation.
    private final long reuseGraceMs;

    public RefreshTokenServiceImpl(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.refresh-token.expiration-ms}") long expirationMs,
            @Value("${app.refresh-token.reuse-grace-ms:5000}") long reuseGraceMs) {

        if (expirationMs <= 0) {
            throw new IllegalStateException(
                    "app.refresh-token.expiration-ms must be positive");
        }

        if (reuseGraceMs < 0) {
            throw new IllegalStateException(
                    "app.refresh-token.reuse-grace-ms must not be negative");
        }

        this.refreshTokenRepository = refreshTokenRepository;
        this.expirationMs = expirationMs;
        this.reuseGraceMs = reuseGraceMs;

        log.info(
                "Refresh token service initialized with expiration of {} ms and reuse grace of {} ms",
                expirationMs,
                reuseGraceMs);
    }

    @Override
    @Transactional
    public String createFor(User user) {
        return issue(user, Instant.now().plusMillis(expirationMs));
    }

    @Override
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public RotatedToken rotate(String rawToken) {
        // The controller validates @NotBlank, but the service must not depend on it: hashing null throws NPE.
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidRefreshTokenException();
        }
        Instant now = Instant.now();
        String tokenHash = TokenUtils.hash(rawToken);

        RefreshToken current = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> {
                    log.warn("Refresh token rotation rejected because the token was not found");
                    return new InvalidRefreshTokenException();
                });

        // Save user id before bulk update clears the persistence context.
        Long userId = current.getUser().getId();

        if (current.getRevokedAt() != null) {
            handleRevokedTokenReuse(current, userId, now);
            throw new InvalidRefreshTokenException();
        }

        if (!current.getExpiresAt().isAfter(now)) {
            log.warn(
                    "Refresh token rotation rejected for user id {} because it is expired",
                    userId);
            throw new InvalidRefreshTokenException();
        }

        // Ensure single-use token rotation.
        if (refreshTokenRepository.revokeIfActive(tokenHash, now) != 1) {
            log.warn(
                    "Refresh token rotation rejected for user id {} because it was already consumed",
                    userId);
            throw new InvalidRefreshTokenException();
        }

        User user = current.getUser();
        String replacement = issue(user, current.getExpiresAt());

        log.info("Rotated refresh token for user id {}", userId);

        return new RotatedToken(user, replacement);
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        // Logging out with no token has nothing to revoke; treat it as a no-op rather than an error.
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        int revoked = refreshTokenRepository.revokeIfActive(
                TokenUtils.hash(rawToken),
                Instant.now());

        log.debug("Logout revoked {} refresh token(s)", revoked);
    }

    private String issue(User user, Instant expiresAt) {
        String rawToken = TokenUtils.generateToken();

        RefreshToken token = new RefreshToken();

        // Store only the token hash.
        token.setTokenHash(TokenUtils.hash(rawToken));
        token.setUser(user);
        token.setExpiresAt(expiresAt);

        refreshTokenRepository.save(token);

        log.debug("Created refresh token for user id {}", user.getId());

        return rawToken;
    }

    /** Handle reuse of a revoked token. */
    private void handleRevokedTokenReuse(
            RefreshToken spent,
            Long userId,
            Instant now) {

        Instant graceEnds = spent.getRevokedAt().plusMillis(reuseGraceMs);

        if (now.isBefore(graceEnds)) {
            log.info(
                    "Refresh token reuse for user id {} within grace window; rejected only",
                    userId);
            return;
        }

        int revoked = refreshTokenRepository.revokeAllActiveForUser(userId, now);

        log.warn(
                "Refresh token reuse detected for user id {}; revoked {} active token(s)",
                userId,
                revoked);
    }
}