package com.human.ev_relay_mes.feature.notification.api;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NotificationResponseDto {

    private Long notificationId;
    private String type;
    private String severity;
    private String title;
    private String message;
    private String sourceKey;
    private String linkPath;
    private LocalDateTime occurredAt;
    private LocalDateTime resolvedAt;
    private boolean read;
    private LocalDateTime readAt;
}
