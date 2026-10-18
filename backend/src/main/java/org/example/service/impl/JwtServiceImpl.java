package org.example.service.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.example.service.JwtService;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    // Key used to sign and check tokens.
    private final SecretKey signingKey;

    // Token lifetime in milliseconds.
    @Getter
    private final long expirationMs;

    // Set the key and token lifetime.
    public JwtServiceImpl(
            SecretKey jwtSigningKey,
            @Value("${app.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = jwtSigningKey;
        this.expirationMs = expirationMs;
        log.info("JWT service initialized with expiration of {} ms", expirationMs);
    }

    // Create a signed token for the user.
    @Override
    public String generateToken(@NonNull UserDetails userDetails) {
        log.debug("Generating JWT");
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .id(UUID.randomUUID().toString())
                .claim("roles", userDetails.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .toList())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    // Read the username from the token.
    @Override
    public String extractUsername(@NonNull String token) {
        return extractAllClaims(token).getSubject();
    }

    @Override
    public Date extractExpiration(@NonNull String token) {
        return extractAllClaims(token).getExpiration();
    }

    // Check that the token belongs to this user and is not expired.
    @Override
    public boolean isTokenValid(@NonNull String token, @NonNull UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        boolean valid = userDetails.getUsername().equals(claims.getSubject())
                && claims.getExpiration().after(new Date());
        log.debug("JWT validation completed: {}", valid);
        return valid;
    }

    // Read token data after checking its signature.
    private Claims extractAllClaims(@NonNull String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

}
