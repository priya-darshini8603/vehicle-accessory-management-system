package org.example.dto;


import lombok.*;

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
}
