package com.human.ev_relay_mes.feature.notification.api;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "notifications",
        indexes = {
                @Index(name = "idx_notification_occurred", columnList = "occurred_at"),
                @Index(name = "idx_notification_source", columnList = "notification_type,source_key")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", nullable = false, length = 30)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 20)
    private Severity severity;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Column(name = "message", nullable = false, length = 500)
    private String message;

    @Column(name = "source_key", nullable = false, length = 100)
    private String sourceKey;

    @Column(name = "link_path", nullable = false, length = 255)
    private String linkPath;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public enum Type {
        MACHINE_ALARM,
        LOW_STOCK
    }

    public enum Severity {
        INFO,
        WARN,
        ERROR
    }
}
