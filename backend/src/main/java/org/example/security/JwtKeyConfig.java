
package org.example.security;

import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtKeyConfig {

    // HS256 requires a key of at least 256 bits.
    private static final int MIN_KEY_BYTES = 32;

    /**
     * Builds the HMAC key used to sign JWTs from the Base64-encoded app.jwt.secret property.
     * A fixed, externally supplied secret keeps tokens valid across restarts and multiple instances.
     */
    @Bean
    public SecretKey jwtSigningKey(@Value("${app.jwt.secret:}") String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalStateException(
                    "app.jwt.secret is not set. Provide a Base64-encoded secret of at least "
                            + MIN_KEY_BYTES + " bytes (e.g. JWT_SECRET=$(openssl rand -base64 48)).");
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret.trim());
        } catch (RuntimeException exception) {
            throw new IllegalStateException("app.jwt.secret must be valid Base64", exception);
        }
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException(
                    "app.jwt.secret must decode to at least " + MIN_KEY_BYTES + " bytes");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
