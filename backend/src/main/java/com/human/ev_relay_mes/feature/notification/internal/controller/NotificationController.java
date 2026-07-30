package com.human.ev_relay_mes.feature.notification.internal.controller;

import com.human.ev_relay_mes.feature.auth.api.CustomUserDetails;
import com.human.ev_relay_mes.feature.notification.api.NotificationOperations;
import com.human.ev_relay_mes.feature.notification.api.NotificationResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationOperations notificationOperations;

    @GetMapping
    public List<NotificationResponseDto> getNotifications(
            @AuthenticationPrincipal CustomUserDetails user,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Boolean read) {
        return notificationOperations.getNotifications(
                user.getMemberId(), limit, sort, type, read);
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(
            @AuthenticationPrincipal CustomUserDetails user) {
        return Map.of("count",
                notificationOperations.getUnreadCount(user.getMemberId()));
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationResponseDto markRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserDetails user) {
        return notificationOperations.markRead(
                notificationId, user.getMemberId());
    }

    @PatchMapping("/read-all")
    public void markAllRead(
            @AuthenticationPrincipal CustomUserDetails user) {
        notificationOperations.markAllRead(user.getMemberId());
    }
}
