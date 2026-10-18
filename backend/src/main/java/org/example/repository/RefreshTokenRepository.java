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

    @Modifying
    @Query("""
            update RefreshToken token
            set token.revokedAt = :now
            where token.tokenHash = :tokenHash
              and token.user.email = :email
              and token.revokedAt is null
            """)
    int revokeForUser(
            @Param("tokenHash") String tokenHash,
            @Param("email") String email,
            @Param("now") Instant now
    );
}