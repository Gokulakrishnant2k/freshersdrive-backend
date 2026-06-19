package com.freshersdrive;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync       // lets @Async send emails in background threads
@EnableScheduling  // lets @Scheduled run the 9 AM daily reminder cron job
public class FreshersDriveApplication {

    public static void main(String[] args) {
        SpringApplication.run(FreshersDriveApplication.class, args);
    }
}