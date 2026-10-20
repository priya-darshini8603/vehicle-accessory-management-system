package org.example.repository;

import java.time.Instant;
import java.util.Optional;
import org.example.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Single-use consumption: succeeds for exactly one caller even when requests race.
     * Returns 1 if this call revoked the token, 0 otherwise.
     */
    @Modifying
    @Query("""
            update RefreshToken token
            set token.revokedAt = :now
            where token.tokenHash = :tokenHash
              and token.revokedAt is null
              and token.expiresAt > :now
            """)
    int revokeIfActive(
            @Param("tokenHash") String tokenHash,
            @Param("now") Instant now
    );

    /**
     * Replay response: revokes every still-active refresh token of the user.
     * Flushes pending changes first and clears the persistence context afterwards.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken token
            set token.revokedAt = :now
            where token.user.id = :userId
              and token.revokedAt is null
              and token.expiresAt > :now
            """)
    int revokeAllActiveForUser(
            @Param("userId") Long userId,
            @Param("now") Instant now
    );

    // Removes tokens revoked or expired before the cutoff
    @Modifying
    @Query("""
            delete from RefreshToken token
            where (token.revokedAt is not null and token.revokedAt < :cutoff)
               or token.expiresAt < :cutoff
            """)
    int deleteStale(@Param("cutoff") Instant cutoff);
}