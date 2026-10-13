
package org.example.dto;

import org.example.enums.Role;

/**
 * Safe user profile payload returned to API consumers.
 * It excludes sensitive fields such as the password hash.
 */
public record UserResponse(
        Long id,
        String fullName,
        String email,
        String phoneNumber,
        Role role
) {}
