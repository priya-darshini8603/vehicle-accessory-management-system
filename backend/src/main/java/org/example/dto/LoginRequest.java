
package org.example.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Login details sent by the user.
public record LoginRequest(

        @NotBlank
        @Email
        String email,

        @NotBlank
        @Size(max = 72)
        String password
) {}
