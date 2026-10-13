
package org.example.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request payload for user authentication.
 * The email is normalized to lowercase before evaluation in the auth service.
 */
public record LoginRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = 72)
        String password
) {}
