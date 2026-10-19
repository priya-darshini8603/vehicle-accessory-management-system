package org.example.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.example.entity.RefreshToken;
import org.example.entity.User;
import org.example.exception.DuplicateResourceException;
import org.example.exception.InvalidRefreshTokenException;
import org.example.exception.RefreshTokenOwnershipException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.RefreshTokenRepository;
import org.example.security.TokenUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final String RAW = "raw-refresh-token";
    private static final String HASH = TokenUtils.hash(RAW);
    private static final String EMAIL = "owner@example.com";

    @Mock
    private RefreshTokenRepository repository;

    @InjectMocks
    private RefreshTokenServiceImpl service;

    @Test
    void revokeSucceedsForActiveTokenOwnedByUser() {
        RefreshToken token = token(EMAIL, Instant.now().plusSeconds(600), null);
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));
        when(repository.revokeIfActiveForUser(eq(HASH), eq(1L), any(Instant.class))).thenReturn(1);

        service.revoke(RAW, "OWNER@example.com"); // email match is case-insensitive

        verify(repository).revokeIfActiveForUser(eq(HASH), eq(1L), any(Instant.class));
    }

    @Test
    void revokeThrowsNotFoundWhenHashDoesNotExist() {
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(RAW, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Refresh token not found");
        verifyNeverRevoked();
    }

    @Test
    void revokeThrowsNotFoundForRandomToken() {
        when(repository.findByTokenHashForUpdate(TokenUtils.hash("random-garbage")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke("random-garbage", EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
        verifyNeverRevoked();
    }

    @Test
    void revokeThrowsForbiddenWhenTokenBelongsToAnotherUser() {
        RefreshToken token = token("someone-else@example.com", Instant.now().plusSeconds(600), null);
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revoke(RAW, EMAIL))
                .isInstanceOf(RefreshTokenOwnershipException.class)
                .hasMessage("Refresh token does not belong to authenticated user");
        verifyNeverRevoked();
    }

    @Test
    void revokeThrowsConflictWhenAlreadyRevoked() {
        RefreshToken token = token(EMAIL, Instant.now().plusSeconds(600), Instant.now().minusSeconds(5));
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revoke(RAW, EMAIL))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Refresh token already revoked");
        verifyNeverRevoked();
    }

    @Test
    void revokeThrowsUnauthorizedWhenExpired() {
        RefreshToken token = token(EMAIL, Instant.now().minusSeconds(1), null);
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revoke(RAW, EMAIL))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token expired");
        verifyNeverRevoked();
    }

    @Test
    void ownershipIsCheckedBeforeRevokedState() {
        // A revoked token of another user must not leak its state: expect 403, not 409.
        RefreshToken token = token("someone-else@example.com", Instant.now().plusSeconds(600), Instant.now());
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revoke(RAW, EMAIL))
                .isInstanceOf(RefreshTokenOwnershipException.class);
        verifyNeverRevoked();
    }

    @Test
    void revokeThrowsConflictWhenConditionalUpdateLosesRace() {
        RefreshToken token = token(EMAIL, Instant.now().plusSeconds(600), null);
        when(repository.findByTokenHashForUpdate(HASH)).thenReturn(Optional.of(token));
        when(repository.revokeIfActiveForUser(eq(HASH), eq(1L), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.revoke(RAW, EMAIL))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Refresh token already revoked");
    }

    @Test
    void revokeRejectsBlankToken() {
        assertThatThrownBy(() -> service.revoke("  ", EMAIL))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(repository, never()).findByTokenHashForUpdate(anyString());
    }

    @Test
    void hashingIsDeterministicAndDoesNotExposeRawToken() {
        assertThat(TokenUtils.hash(RAW)).isEqualTo(HASH).hasSize(64).isNotEqualTo(RAW);
    }

    private void verifyNeverRevoked() {
        verify(repository, never()).revokeIfActiveForUser(anyString(), anyLong(), any(Instant.class));
    }

    private static RefreshToken token(String ownerEmail, Instant expiresAt, Instant revokedAt) {
        User user = new User();
        user.setId(1L);
        user.setEmail(ownerEmail);
        RefreshToken token = new RefreshToken();
        token.setTokenHash(HASH);
        token.setUser(user);
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }
}