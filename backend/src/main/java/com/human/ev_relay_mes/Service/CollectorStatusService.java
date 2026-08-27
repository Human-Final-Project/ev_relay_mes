package com.human.ev_relay_mes.Service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class CollectorStatusService {

    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(10);

    private volatile CollectorSnapshot latest =
            new CollectorSnapshot(List.of(), 0, null);

    public void receiveHeartbeat(List<String> connectedMachineIds, int totalCapacity) {
        List<String> normalizedIds = connectedMachineIds == null
                ? List.of()
                : connectedMachineIds.stream()
                        .filter(id -> id != null && !id.isBlank())
                        .map(String::trim)
                        .distinct()
                        .sorted()
                        .toList();
        latest = new CollectorSnapshot(
                normalizedIds,
                Math.max(totalCapacity, normalizedIds.size()),
                LocalDateTime.now());
    }

    public CollectorStatus getStatus() {
        CollectorSnapshot snapshot = latest;
        LocalDateTime checkedAt = LocalDateTime.now();
        boolean online = snapshot.lastHeartbeatAt() != null
                && Duration.between(snapshot.lastHeartbeatAt(), checkedAt)
                        .compareTo(ONLINE_TIMEOUT) <= 0;
        List<String> connectedIds = online
                ? snapshot.connectedMachineIds()
                : List.of();
        return new CollectorStatus(
                online,
                connectedIds.size(),
                snapshot.totalCapacity(),
                connectedIds,
                snapshot.lastHeartbeatAt(),
                checkedAt);
    }

    private record CollectorSnapshot(
            List<String> connectedMachineIds,
            int totalCapacity,
            LocalDateTime lastHeartbeatAt) {
    }

    public record CollectorStatus(
            boolean l2Online,
            int connectedL1Count,
            int totalL1Count,
            List<String> connectedMachineIds,
            LocalDateTime lastHeartbeatAt,
            LocalDateTime checkedAt) {
    }
}
