package org.example.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.example.entity.Vehicle;

// A customer adding one of their own vehicles. There is no userId: the owner is always the caller.
public record GarageVehicleRequest(

        @NotBlank(message = "Vehicle number is required")
        @Size(min = 4, max = 32, message = "Vehicle number must contain 4 to 32 characters")
        @Pattern(regexp = "^[A-Z0-9-]*$",
                message = "Vehicle number may only contain letters, digits and hyphens")
        String vehicleNumber,

        @NotBlank(message = "Make is required")
        @Size(max = 60, message = "Make must not exceed 60 characters")
        String make,

        @NotBlank(message = "Model is required")
        @Size(max = 60, message = "Model must not exceed 60 characters")
        String model,

        @NotNull(message = "Year is required")
        @Min(value = 1886, message = "Year must be 1886 or later")
        @Max(value = 2100, message = "Year must be 2100 or earlier")
        Integer year,

        @Size(max = 40, message = "Nickname must not exceed 40 characters")
        String nickname
) {
    public GarageVehicleRequest {
        vehicleNumber = Vehicle.normalizeVehicleNumber(vehicleNumber);
        make = make == null ? null : make.trim();
        model = model == null ? null : model.trim();
        nickname = GarageVehicleUpdateRequest.normalizeNickname(nickname);
    }

    @JsonIgnore
    @AssertTrue(message = "Year must not be later than next year")
    public boolean isYearPlausible() {
        return Vehicle.isPlausibleModelYear(year);
    }

    // Lets VehicleMapper copy the shared fields.
    public VehicleRequest toVehicleRequest() {
        return new VehicleRequest(vehicleNumber, make, model, year, null);
    }
}
