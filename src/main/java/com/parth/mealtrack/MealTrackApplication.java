package com.parth.mealtrack;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MealTrackApplication {
    public static void main(String[] args) {
        // Cutoff times are IST; also avoids the legacy "Asia/Calcutta" id that PostgreSQL rejects.
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
        SpringApplication.run(MealTrackApplication.class, args);
    }
}
