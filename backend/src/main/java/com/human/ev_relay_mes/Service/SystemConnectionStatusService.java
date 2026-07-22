package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.CollectorStatusRequestDto;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SystemConnectionStatusService {

    private static final Duration ONLINE_WINDOW = Duration.ofSeconds(12);
    private static final int DEFAULT_L1_COUNT = 6;

    private final AtomicReference<CollectorSnapshot> latest = new AtomicReference<>();

    public void report(CollectorStatusRequestDto request) {
        latest.set(new CollectorSnapshot(
                request.collectorId(), request.connectedL1(), request.totalL1(), Instant.now()));
    }

    public SystemConnectionStatus getStatus() {
        CollectorSnapshot snapshot = latest.get();
        Instant now = Instant.now();
        boolean l2Online = snapshot != null
                && Duration.between(snapshot.reportedAt(), now).compareTo(ONLINE_WINDOW) <= 0;
        int totalL1 = snapshot == null ? DEFAULT_L1_COUNT : snapshot.totalL1();
        int connectedL1 = l2Online ? snapshot.connectedL1() : 0;
        String l1Status = !l2Online || connectedL1 == 0
                ? "OFFLINE"
                : connectedL1 == totalL1 ? "ONLINE" : "PARTIAL";

        return new SystemConnectionStatus(
                new L2Status(l2Online ? "ONLINE" : "OFFLINE",
                        snapshot == null ? null : snapshot.collectorId(),
                        snapshot == null ? null : snapshot.reportedAt()),
                new L1Status(l1Status, connectedL1, totalL1));
    }

    private record CollectorSnapshot(
            String collectorId, int connectedL1, int totalL1, Instant reportedAt) {
    }

    public record SystemConnectionStatus(L2Status l2, L1Status l1) {
    }

    public record L2Status(String status, String collectorId, Instant lastSeenAt) {
    }

    public record L1Status(String status, int connected, int total) {
    }
}
