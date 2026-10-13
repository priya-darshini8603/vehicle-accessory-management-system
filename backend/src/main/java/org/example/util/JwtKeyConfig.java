
package org.example.util;

import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtKeyConfig {

    /**
     * Creates a cryptographic key used to sign the JWTs issued to authenticated users.
     */
    @Bean
    public SecretKey jwtSigningKey() {
        return Jwts.SIG.HS256.key().build();
    }
}
