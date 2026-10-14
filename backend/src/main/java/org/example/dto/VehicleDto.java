package org.example.dto;


import lombok.*;

import java.util.Set;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Data
public class VehicleDto {
    private Long id;
    private String vehicleNumber;
    private String make;
    private String model;
    private Integer year;
    private Long userId;
    private Set<Long> accessoryIds;

}
