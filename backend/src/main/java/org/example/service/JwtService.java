
package org.example.service;

import java.util.Date;

import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.UserDetails;

public interface JwtService {

    String generateToken(@NonNull UserDetails userDetails);

    String extractUsername(@NonNull String token);

    Date extractExpiration(@NonNull String token);

    boolean isTokenValid(
            @NonNull String token,
            @NonNull UserDetails userDetails);

    long getExpirationMs();
}
