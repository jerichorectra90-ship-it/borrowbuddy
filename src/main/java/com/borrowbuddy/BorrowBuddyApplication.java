package com.borrowbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // runs the daily overdue email check
public class BorrowBuddyApplication {
    public static void main(String[] args) {
        SpringApplication.run(BorrowBuddyApplication.class, args);
    }
}
