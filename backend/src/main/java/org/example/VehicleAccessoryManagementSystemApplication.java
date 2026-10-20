package org.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
@Slf4j
public class VehicleAccessoryManagementSystemApplication {

    public static void main(String[] args) {
        log.info("Starting Vehicle Accessory Management System");
        SpringApplication.run(VehicleAccessoryManagementSystemApplication.class, args);
    }
}
