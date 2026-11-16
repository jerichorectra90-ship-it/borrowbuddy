package com.borrowbuddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // need this or the daily overdue email check won't run
public class BorrowBuddyApplication {
    public static void main(String[] args) {
        SpringApplication.run(BorrowBuddyApplication.class, args);
    }
}
