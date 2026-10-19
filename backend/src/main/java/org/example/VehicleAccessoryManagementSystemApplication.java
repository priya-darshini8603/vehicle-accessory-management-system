package org.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@Slf4j
public class VehicleAccessoryManagementSystemApplication {

    public static void main(String[] args) {
        log.info("Starting Vehicle Accessory Management System");
        SpringApplication.run(VehicleAccessoryManagementSystemApplication.class, args);
    }
}
