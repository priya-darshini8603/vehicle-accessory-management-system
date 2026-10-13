package org.example.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.Getter;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    /**
     * Secret key used to sign and validate JWTs issued by the application.
     */
    private final SecretKey signingKey;

    /**
     * JWT lifetime in milliseconds, configured from application.properties.
     */
    @Getter
    private final long expirationMs;

    public JwtService(
            SecretKey jwtSigningKey,
            @Value("${app.jwt.expiration-ms:1800000}") long expirationMs) {
        this.signingKey = jwtSigningKey;
        this.expirationMs = expirationMs;
    }

    /**
     * Generates a signed JWT containing the username and granted roles.
     */
    public String generateToken(@NonNull UserDetails userDetails) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public String extractUsername(@NonNull String token) {
        return extractAllClaims(token).getSubject();
    }

    /**
     * Validates both the token subject and expiry timestamp against the authenticated user.
     */
    public boolean isTokenValid(@NonNull String token, @NonNull UserDetails userDetails) {
        Claims claims = extractAllClaims(token);

        return claims.getSubject().equals(userDetails.getUsername())
                && claims.getExpiration().after(new Date());
    }

    private Claims extractAllClaims(@NonNull String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}