package com.human.ev_relay_mes.Service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CollectorStatusServiceTest {

    @Test
    void reportsOfflineUntilFirstHeartbeat() {
        CollectorStatusService service = new CollectorStatusService();

        CollectorStatusService.CollectorStatus status = service.getStatus();

        assertFalse(status.l2Online());
        assertEquals(0, status.connectedL1Count());
        assertTrue(status.connectedMachineIds().isEmpty());
    }

    @Test
    void heartbeatReportsDistinctConnectedMachines() {
        CollectorStatusService service = new CollectorStatusService();

        service.receiveHeartbeat(
                List.of("EQ-WELD-01", "EQ-WIND-01", "EQ-WIND-01"),
                6);
        CollectorStatusService.CollectorStatus status = service.getStatus();

        assertTrue(status.l2Online());
        assertEquals(2, status.connectedL1Count());
        assertEquals(6, status.totalL1Count());
        assertEquals(
                List.of("EQ-WELD-01", "EQ-WIND-01"),
                status.connectedMachineIds());
    }
}
