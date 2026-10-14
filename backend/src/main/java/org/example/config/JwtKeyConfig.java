
package org.example.config;

import io.jsonwebtoken.Jwts;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class JwtKeyConfig {

    // Creates the key used to sign JWT tokens.
    @Bean
    public SecretKey jwtSigningKey() {
        log.info("Generating JWT signing key");
        return Jwts.SIG.HS256.key().build();
    }
}
