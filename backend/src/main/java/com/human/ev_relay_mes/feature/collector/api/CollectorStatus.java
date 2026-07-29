package com.human.ev_relay_mes.feature.collector.api;

import java.time.LocalDateTime;
import java.util.List;

public record CollectorStatus(
        boolean l2Online,
        int connectedL1Count,
        int totalL1Count,
        List<String> connectedMachineIds,
        LocalDateTime lastHeartbeatAt,
        LocalDateTime checkedAt) {
}
