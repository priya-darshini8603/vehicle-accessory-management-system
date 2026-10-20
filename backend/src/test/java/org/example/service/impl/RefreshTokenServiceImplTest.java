package org.example.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.example.entity.RefreshToken;
import org.example.entity.User;
import org.example.exception.InvalidRefreshTokenException;
import org.example.repository.RefreshTokenRepository;
import org.example.security.TokenUtils;
import org.example.service.RefreshTokenService.RotatedToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceImplTest {

    private static final String RAW = "raw-refresh-token";
    private static final String HASH = TokenUtils.hash(RAW);
    private static final long USER_ID = 1L;
    private static final long EXPIRATION_MS = Duration.ofDays(30).toMillis();
    private static final long GRACE_MS = 5_000L;

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenServiceImpl(repository, EXPIRATION_MS, GRACE_MS);
    }

    // ---- createFor -------------------------------------------------------------------------

    @Test
    void createForStoresOnlyTheHashWithFullLifetime() {
        User user = user();
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        Instant before = Instant.now();
        String raw = service.createFor(user);
        Instant after = Instant.now();

        verify(repository).save(captor.capture());
        RefreshToken saved = captor.getValue();
        assertThat(saved.getTokenHash()).isEqualTo(TokenUtils.hash(raw)).isNotEqualTo(raw);
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getExpiresAt())
                .isBetween(before.plusMillis(EXPIRATION_MS), after.plusMillis(EXPIRATION_MS));
    }

    @Test
    void createForReturnsDistinctTokensOnEveryCall() {
        String first = service.createFor(user());
        String second = service.createFor(user());

        assertThat(first).isNotEqualTo(second);
    }

    // ---- rotate: happy path and absolute lifetime ---------------------------------------------

    @Test
    void rotateIssuesReplacementThatInheritsTheOriginalExpiry() {
        // 10 days remain. A buggy "now + 30 days" reset would produce a clearly different value.
        Instant originalExpiry = Instant.now().plus(Duration.ofDays(10));
        RefreshToken current = token(originalExpiry, null);
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(current));
        when(repository.revokeIfActive(eq(HASH), any(Instant.class))).thenReturn(1);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        RotatedToken rotated = service.rotate(RAW);

        verify(repository).save(captor.capture());
        RefreshToken replacement = captor.getValue();
        assertThat(replacement.getExpiresAt()).isEqualTo(originalExpiry);
        assertThat(replacement.getTokenHash()).isEqualTo(TokenUtils.hash(rotated.refreshToken()));
        assertThat(rotated.user()).isSameAs(current.getUser());
        verify(repository, never()).revokeAllActiveForUser(anyLong(), any(Instant.class));
    }

    @Test
    void rotateNearTheEndOfTheSessionDoesNotExtendIt() {
        Instant almostExpired = Instant.now().plusSeconds(2);
        RefreshToken current = token(almostExpired, null);
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(current));
        when(repository.revokeIfActive(eq(HASH), any(Instant.class))).thenReturn(1);
        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);

        service.rotate(RAW);

        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getExpiresAt()).isEqualTo(almostExpired);
    }

    // ---- rotate: rejections -------------------------------------------------------------------

    @Test
    void rotateRejectsUnknownToken() {
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidRefreshTokenException.class);

        verifyNothingWritten();
    }

    @Test
    void rotateRejectsNullAndBlankWithoutTouchingTheRepository() {
        assertThatThrownBy(() -> service.rotate(null)).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> service.rotate("   ")).isInstanceOf(InvalidRefreshTokenException.class);

        verifyNoInteractions(repository);
    }

    @Test
    void rotateRejectsExpiredTokenWithoutRevokingTheFamily() {
        RefreshToken current = token(Instant.now().minusSeconds(1), null);
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(current));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidRefreshTokenException.class);

        verifyNothingWritten();
    }

    @Test
    void rotateRejectsWithoutFamilyRevocationWhenConditionalUpdateLosesRace() {
        RefreshToken current = token(Instant.now().plusSeconds(600), null);
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(current));
        when(repository.revokeIfActive(eq(HASH), any(Instant.class))).thenReturn(0);

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository, never()).revokeAllActiveForUser(anyLong(), any(Instant.class));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    // ---- rotate: replay detection -------------------------------------------------------------

    @Test
    void replayOutsideGraceWindowRevokesAllActiveTokensOfTheUser() {
        Instant revokedLongAgo = Instant.now().minusSeconds(60);
        RefreshToken spent = token(Instant.now().plusSeconds(600), revokedLongAgo);
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(spent));
        when(repository.revokeAllActiveForUser(eq(USER_ID), any(Instant.class))).thenReturn(2);

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeAllActiveForUser(eq(USER_ID), any(Instant.class));
        verify(repository, never()).revokeIfActive(anyString(), any(Instant.class));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    @Test
    void replayWithinGraceWindowIsOnlyRejected() {
        Instant revokedJustNow = Instant.now().minusMillis(1_000);
        RefreshToken spent = token(Instant.now().plusSeconds(600), revokedJustNow);
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidRefreshTokenException.class);

        verifyNothingWritten();
    }

    @Test
    void replayWithZeroGraceWindowAlwaysRevokesTheFamily() {
        service = new RefreshTokenServiceImpl(repository, EXPIRATION_MS, 0);
        RefreshToken spent = token(Instant.now().plusSeconds(600), Instant.now().minusMillis(1));
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeAllActiveForUser(eq(USER_ID), any(Instant.class));
    }

    @Test
    void replayOfASpentTokenThatHasAlsoExpiredStillTriggersFamilyRevocation() {
        // Revocation is checked before expiry on purpose: presenting a spent token is the
        // breach signal, regardless of whether its lifetime also ran out.
        RefreshToken spent = token(Instant.now().minusSeconds(10), Instant.now().minusSeconds(3_600));
        when(repository.findByTokenHash(HASH)).thenReturn(Optional.of(spent));

        assertThatThrownBy(() -> service.rotate(RAW)).isInstanceOf(InvalidRefreshTokenException.class);

        verify(repository).revokeAllActiveForUser(eq(USER_ID), any(Instant.class));
    }

    // ---- revoke (logout) ----------------------------------------------------------------------

    @Test
    void revokeRevokesActiveTokenByHashOnly() {
        when(repository.revokeIfActive(eq(HASH), any(Instant.class))).thenReturn(1);

        service.revoke(RAW);

        verify(repository).revokeIfActive(eq(HASH), any(Instant.class));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void revokeIsIdempotentForUnknownExpiredOrAlreadyRevokedTokens() {
        when(repository.revokeIfActive(eq(TokenUtils.hash("random-garbage")), any(Instant.class)))
                .thenReturn(0);

        assertThatCode(() -> service.revoke("random-garbage")).doesNotThrowAnyException();
    }

    @Test
    void revokeIgnoresNullAndBlankWithoutTouchingTheRepository() {
        assertThatCode(() -> service.revoke(null)).doesNotThrowAnyException();
        assertThatCode(() -> service.revoke("  ")).doesNotThrowAnyException();

        verifyNoInteractions(repository);
    }

    @Test
    void revokeNeverRevokesTheWholeFamily() {
        when(repository.revokeIfActive(eq(HASH), any(Instant.class))).thenReturn(1);

        service.revoke(RAW);

        verify(repository, never()).revokeAllActiveForUser(anyLong(), any(Instant.class));
    }

    // ---- configuration and hashing ------------------------------------------------------------

    @Test
    void constructorRejectsNonPositiveExpiration() {
        assertThatThrownBy(() -> new RefreshTokenServiceImpl(repository, 0, GRACE_MS))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new RefreshTokenServiceImpl(repository, -5, GRACE_MS))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void constructorRejectsNegativeGraceWindow() {
        assertThatThrownBy(() -> new RefreshTokenServiceImpl(repository, EXPIRATION_MS, -1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void hashingIsDeterministicAndDoesNotExposeRawToken() {
        assertThat(TokenUtils.hash(RAW)).isEqualTo(HASH).hasSize(64).isNotEqualTo(RAW);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private void verifyNothingWritten() {
        verify(repository, never()).revokeIfActive(anyString(), any(Instant.class));
        verify(repository, never()).revokeAllActiveForUser(anyLong(), any(Instant.class));
        verify(repository, never()).save(any(RefreshToken.class));
    }

    private static User user() {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("owner@example.com");
        return user;
    }

    private static RefreshToken token(Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(HASH);
        token.setUser(user());
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }
}