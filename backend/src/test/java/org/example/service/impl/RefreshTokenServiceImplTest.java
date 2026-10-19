package org.example.service.impl;

import org.example.entity.RefreshToken;
import org.example.entity.User;
import org.example.exception.DuplicateResourceException;
import org.example.exception.InvalidRefreshTokenException;
import org.example.exception.ResourceNotFoundException;
import org.example.repository.RefreshTokenRepository;
import org.example.security.TokenUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final String RAW_TOKEN = "opaque-refresh-token";
    private static final String EMAIL = "user@example.com";

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(refreshTokenRepository);
    }

    @Test
    void revokeRejectsTokenThatDoesNotExist() {
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.hash(RAW_TOKEN)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(RAW_TOKEN, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Refresh token not found");
        verify(refreshTokenRepository, never()).revokeIfActiveForUser(
                any(), any(), any());
    }

    @Test
    void revokeRejectsRandomTokenWhenItsHashDoesNotExist() {
        String randomToken = "random-invalid-refresh-token";
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.hash(randomToken)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(randomToken, EMAIL))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Refresh token not found");
        verify(refreshTokenRepository, never()).revokeIfActiveForUser(
                any(), any(), any());
    }

    @Test
    void revokeRejectsTokenOwnedByAnotherUser() {
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(tokenFor("other@example.com")));

        assertThatThrownBy(() -> service.revoke(RAW_TOKEN, EMAIL))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("Refresh token does not belong to authenticated user");
    }

    @Test
    void revokeRejectsAlreadyRevokedToken() {
        RefreshToken token = tokenFor(EMAIL);
        token.setRevokedAt(Instant.now());
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revoke(RAW_TOKEN, EMAIL))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Refresh token already revoked");
    }

    @Test
    void revokeRejectsExpiredToken() {
        RefreshToken token = tokenFor(EMAIL);
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> service.revoke(RAW_TOKEN, EMAIL))
                .isInstanceOf(InvalidRefreshTokenException.class)
                .hasMessage("Refresh token expired");
    }

    @Test
    void revokeRejectsTokenConsumedByAnotherConcurrentRequest() {
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(tokenFor(EMAIL)));
        when(refreshTokenRepository.revokeIfActiveForUser(
                eq(TokenUtils.hash(RAW_TOKEN)), any(), any(Instant.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.revoke(RAW_TOKEN, EMAIL))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Refresh token already revoked");
    }

    @Test
    void revokePersistsRevocationForValidToken() {
        when(refreshTokenRepository.findByTokenHashForUpdate(TokenUtils.hash(RAW_TOKEN)))
                .thenReturn(Optional.of(tokenFor(EMAIL)));
        when(refreshTokenRepository.revokeIfActiveForUser(
                eq(TokenUtils.hash(RAW_TOKEN)), any(), any(Instant.class)))
                .thenReturn(1);

        service.revoke(RAW_TOKEN, EMAIL);

        verify(refreshTokenRepository).revokeIfActiveForUser(
                eq(TokenUtils.hash(RAW_TOKEN)), any(), any(Instant.class));
    }

    private static RefreshToken tokenFor(String email) {
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setExpiresAt(Instant.now().plusSeconds(60));
        return token;
    }
}
