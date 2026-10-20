
package org.example.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// User signup details.
public record RegisterRequest(

        @NotBlank(message = "Full name is required")
        @Size(max = 100)
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email")
        @Size(max = 254)
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 12, max = 72,
                message = "Password must contain 12 to 72 characters")
        String password,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^$|^[0-9+() -]{7,20}$",
                message = "Invalid phone number")
        String phoneNumber
) {}
