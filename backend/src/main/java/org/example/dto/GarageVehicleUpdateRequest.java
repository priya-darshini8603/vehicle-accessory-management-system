package org.example.dto;

import jakarta.validation.constraints.Size;

// PATCH /api/me/vehicles/{id}. Only the nickname is customer-editable: the number, make, model and
// year drive accessory compatibility, so corrections go through an admin.
public record GarageVehicleUpdateRequest(

        // null or blank removes the nickname
        @Size(max = 40, message = "Nickname must not exceed 40 characters")
        String nickname
) {
    public GarageVehicleUpdateRequest {
        nickname = normalizeNickname(nickname);
    }

    static String normalizeNickname(String nickname) {
        return nickname == null || nickname.isBlank() ? null : nickname.trim();
    }
}
