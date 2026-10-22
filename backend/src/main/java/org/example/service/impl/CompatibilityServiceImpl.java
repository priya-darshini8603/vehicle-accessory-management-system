package org.example.service.impl;

import lombok.RequiredArgsConstructor;
import org.example.dto.CompatibilityResponse;
import org.example.entity.Vehicle;
import org.example.exception.ResourceNotFoundException;
import org.example.mapper.VehicleMapper;
import org.example.repository.VehicleRepository;
import org.example.service.CompatibilityService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompatibilityServiceImpl implements CompatibilityService {

    private final VehicleRepository vehicleRepository;
    private final VehicleMapper vehicleMapper;

    // Vehicle numbers are stored normalized, so normalizing the input gives a trimmed,
    // case-insensitive search that still uses the unique index. One query loads the accessories.
    @Override
    @Transactional(readOnly = true)
    public CompatibilityResponse findByVehicleNumber(String vehicleNumber) {
        String normalized = Vehicle.normalizeVehicleNumber(vehicleNumber);
        return vehicleRepository.findByVehicleNumberWithAccessories(normalized)
                .map(vehicleMapper::toCompatibilityResponse)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Vehicle with number '" + normalized + "' not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompatibilityResponse> findByModelName(String modelName) {
        // Keep input canonicalization identical to Vehicle.modelKey, then escape LIKE metacharacters.
        String escapedModelName = Vehicle.normalizeModel(modelName)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        String modelKeyPattern = escapedModelName + "%";

        return vehicleRepository.findByModelKeyPatternWithAccessories(modelKeyPattern).stream()
                .map(vehicleMapper::toCompatibilityResponse)
                .sorted(Comparator.comparing(
                                (CompatibilityResponse response) -> response.vehicle().make(),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(response -> response.vehicle().model(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(response -> response.vehicle().year())
                        .thenComparing(response -> response.vehicle().vehicleNumber()))
                .toList();
    }
}
