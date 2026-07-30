package com.human.ev_relay_mes.feature.notification.api;

import com.human.ev_relay_mes.feature.machine.api.MachineAlarmChangedEvent;

import java.util.List;

public interface NotificationOperations {

    List<NotificationResponseDto> getNotifications(
            Long memberId, Integer limit, String sort, String type, Boolean read);

    long getUnreadCount(Long memberId);

    NotificationResponseDto markRead(Long notificationId, Long memberId);

    void markAllRead(Long memberId);

    void applyMachineAlarmChange(MachineAlarmChangedEvent event);

    void evaluateLowStock(String itemCode);
}
