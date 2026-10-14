
package org.example.dto;

import org.example.enums.Role;

// Safe user data sent back to the client.
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        Role role
) {}
