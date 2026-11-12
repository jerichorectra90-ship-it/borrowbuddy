package com.borrowbuddy.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

// A log entry for every email the system tries to send, shown on the admin dashboard.
@Entity
@Table(name = "notifications")
public class Notification {
    public static final String SENT = "Sent";
    public static final String FAILED = "Failed";
    public static final String NOT_CONFIGURED = "Not configured";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime sentAt;
    private String recipient;
    private String subject;
    private String status;

    @Column(length = 500)
    private String detail;

    protected Notification() {}

    public Notification(String recipient, String subject, String status, String detail) {
        this.sentAt = LocalDateTime.now(BorrowRecord.ZONE);
        this.recipient = recipient;
        this.subject = subject;
        this.status = status;
        this.detail = detail == null ? null : detail.substring(0, Math.min(detail.length(), 500));
    }

    public Long getId() { return id; }
    public LocalDateTime getSentAt() { return sentAt; }
    public String getRecipient() { return recipient; }
    public String getSubject() { return subject; }
    public String getStatus() { return status; }
    public String getDetail() { return detail; }
    public boolean isSent() { return SENT.equals(status); }
}
