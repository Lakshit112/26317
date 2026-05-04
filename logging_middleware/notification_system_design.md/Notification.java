package com.notifications.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_user_id", columnList = "userId"),
    @Index(name = "idx_created_at", columnList = "createdAt"),
    @Index(name = "idx_is_read", columnList = "isRead")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(nullable = false, length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationPriority priority;

    @Column(nullable = false)
    private boolean isRead;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    private Instant readAt;

    private Instant expiresAt;

    // Optional deep-link or action URL
    private String actionUrl;

    // Source service that generated the notification
    private String sourceService;

    // Arbitrary reference (e.g., orderId, postId)
    private String referenceId;

    @Enumerated(EnumType.STRING)
    private ReferenceType referenceType;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        isRead = false;
    }

    public enum NotificationType {
        SYSTEM,       // Platform-level system messages
        ALERT,        // Security or urgency alerts
        PROMOTION,    // Marketing or offers
        SOCIAL,       // Friend requests, comments, likes
        TRANSACTION,  // Payment, order updates
        REMINDER      // Scheduled reminders
    }

    public enum NotificationPriority {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum ReferenceType {
        ORDER, POST, USER, PAYMENT, PRODUCT, GENERIC
    }
}
