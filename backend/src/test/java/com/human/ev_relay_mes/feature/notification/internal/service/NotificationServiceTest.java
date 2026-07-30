package com.human.ev_relay_mes.feature.notification.internal.service;

import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmChangedEvent;
import com.human.ev_relay_mes.feature.material.api.MaterialInventory;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.notification.api.Notification;
import com.human.ev_relay_mes.feature.notification.api.NotificationRead;
import com.human.ev_relay_mes.feature.notification.internal.repository.NotificationReadRepository;
import com.human.ev_relay_mes.feature.notification.internal.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationReadRepository readRepository;
    @Mock MemberLookup memberLookup;
    @Mock MaterialInventory materialInventory;
    @Mock MasterDataLookup masterDataLookup;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(
                notificationRepository,
                readRepository,
                memberLookup,
                materialInventory,
                masterDataLookup);
    }

    @Test
    void createsOneNotificationForMachineAlarm() {
        MachineAlarmChangedEvent event = new MachineAlarmChangedEvent(
                12L, "EQ-SEAL-01", "진공 펌프 이상", "ERROR",
                "pump failure", LocalDateTime.now(), false);
        when(notificationRepository.existsByTypeAndSourceKey(
                Notification.Type.MACHINE_ALARM, "12")).thenReturn(false);

        service.applyMachineAlarmChange(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getSeverity()).isEqualTo(Notification.Severity.ERROR);
        assertThat(captor.getValue().getLinkPath()).isEqualTo("/alarms");
        assertThat(captor.getValue().getTitle()).contains("EQ-SEAL-01");
    }

    @Test
    void lowStockCreatesOnceAndResolvesAfterReplenishment() {
        Item item = Item.builder()
                .itemCode("RM-CU-001")
                .itemName("구리선")
                .build();
        when(materialInventory.getAvailableQuantity("RM-CU-001"))
                .thenReturn(80L, 150L);
        when(notificationRepository
                .findByTypeAndSourceKeyAndResolvedAtIsNullOrderByOccurredAtDesc(
                        Notification.Type.LOW_STOCK, "RM-CU-001"))
                .thenReturn(List.of(), List.of(notification(1L)));
        when(masterDataLookup.getRequiredItem("RM-CU-001")).thenReturn(item);

        service.evaluateLowStock("RM-CU-001");
        Notification active = notification(1L);
        when(notificationRepository
                .findByTypeAndSourceKeyAndResolvedAtIsNullOrderByOccurredAtDesc(
                        Notification.Type.LOW_STOCK, "RM-CU-001"))
                .thenReturn(List.of(active));
        service.evaluateLowStock("RM-CU-001");

        verify(notificationRepository, times(1)).save(any(Notification.class));
        assertThat(active.getResolvedAt()).isNotNull();
    }

    @Test
    void markingReadCreatesMemberSpecificReceipt() {
        Notification notification = notification(7L);
        Member member = Member.builder().memberId(3L).build();
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(notification));
        when(readRepository
                .findByNotification_NotificationIdAndMember_MemberId(7L, 3L))
                .thenReturn(Optional.empty());
        when(memberLookup.getRequiredById(3L)).thenReturn(member);
        when(readRepository.save(any(NotificationRead.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.markRead(7L, 3L);

        assertThat(response.isRead()).isTrue();
        verify(readRepository).save(any(NotificationRead.class));
    }

    private Notification notification(Long id) {
        return Notification.builder()
                .notificationId(id)
                .type(Notification.Type.LOW_STOCK)
                .severity(Notification.Severity.WARN)
                .title("재고 부족")
                .message("재고 부족")
                .sourceKey("RM-CU-001")
                .linkPath("/materials")
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
