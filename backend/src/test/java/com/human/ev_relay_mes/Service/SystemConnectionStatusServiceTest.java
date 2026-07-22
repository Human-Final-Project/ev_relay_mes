package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.CollectorStatusRequestDto;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SystemConnectionStatusServiceTest {

    @Test
    void reportsCollectorAndAllL1ConnectionsAsOnline() {
        SystemConnectionStatusService service = new SystemConnectionStatusService();

        service.report(new CollectorStatusRequestDto("L2-COLLECTOR-01", 6, 6));

        SystemConnectionStatusService.SystemConnectionStatus status = service.getStatus();
        assertThat(status.l2().status()).isEqualTo("ONLINE");
        assertThat(status.l2().collectorId()).isEqualTo("L2-COLLECTOR-01");
        assertThat(status.l2().lastSeenAt()).isNotNull();
        assertThat(status.l1().status()).isEqualTo("ONLINE");
        assertThat(status.l1().connected()).isEqualTo(6);
        assertThat(status.l1().total()).isEqualTo(6);
    }

    @Test
    void reportsSomeL1ConnectionsAsPartial() {
        SystemConnectionStatusService service = new SystemConnectionStatusService();

        service.report(new CollectorStatusRequestDto("L2-COLLECTOR-01", 3, 6));

        assertThat(service.getStatus().l1().status()).isEqualTo("PARTIAL");
    }

    @Test
    void startsOfflineBeforeFirstCollectorReport() {
        SystemConnectionStatusService.SystemConnectionStatus status =
                new SystemConnectionStatusService().getStatus();

        assertThat(status.l2().status()).isEqualTo("OFFLINE");
        assertThat(status.l1().status()).isEqualTo("OFFLINE");
        assertThat(status.l1().connected()).isZero();
        assertThat(status.l1().total()).isEqualTo(6);
    }
}
