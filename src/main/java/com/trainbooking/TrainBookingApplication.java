package com.trainbooking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Train Scheduling and Booking System Spring Boot application.
 *
 * @author SLIIT Software Engineering Team
 * @version 1.0.0
 */
@EnableAsync
@EnableScheduling
@SpringBootApplication
public class TrainBookingApplication {

    private static final Logger log = LoggerFactory.getLogger(TrainBookingApplication.class);

    /**
     * Main method to bootstrap and start the Spring Boot application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TrainBookingApplication.class, args);
        log.info("=================================================================");
        log.info("  Train Scheduling & Booking System backend started successfully!");
        log.info("=================================================================");
    }
}
