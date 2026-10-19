package org.example.exception;

import org.springframework.security.access.AccessDeniedException;

public class RefreshTokenOwnershipException extends AccessDeniedException {

    public RefreshTokenOwnershipException() {
        super("Refresh token does not belong to authenticated user");
    }
}
