package org.example.dto;

import jakarta.validation.constraints.NotNull;
import org.example.enums.Role;

// Payload for PUT /api/admin/users/{id}/role.
public record RoleUpdateRequest(

        @NotNull(message = "Role is required (ADMIN or CUSTOMER)")
        Role role
) {}
