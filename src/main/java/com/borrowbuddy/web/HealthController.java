package com.borrowbuddy.web;

import com.borrowbuddy.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Uptime check used by UptimeRobot. It runs a tiny database query so the free
// Render server stays awake AND the free Supabase database never pauses for inactivity.
@RestController
public class HealthController {
    private final UserRepository users;

    public HealthController(UserRepository users) {
        this.users = users;
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        try {
            users.count();
            return ResponseEntity.ok("OK");
        } catch (RuntimeException e) {
            return ResponseEntity.status(503).body("DATABASE DOWN");
        }
    }
}
