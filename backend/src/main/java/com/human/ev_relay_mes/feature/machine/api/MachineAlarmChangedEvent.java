package com.human.ev_relay_mes.feature.machine.api;

import java.time.LocalDateTime;

public record MachineAlarmChangedEvent(
        Long historyId,
        String machineId,
        String alarmName,
        String alarmLevel,
        String message,
        LocalDateTime occurredAt,
        boolean cleared) {
}
