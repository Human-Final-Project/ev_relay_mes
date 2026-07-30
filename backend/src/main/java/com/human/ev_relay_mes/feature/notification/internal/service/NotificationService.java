package com.human.ev_relay_mes.feature.notification.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmChangedEvent;
import com.human.ev_relay_mes.feature.material.api.MaterialInventory;
import com.human.ev_relay_mes.feature.material.api.MaterialStockThreshold;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.notification.api.Notification;
import com.human.ev_relay_mes.feature.notification.api.NotificationOperations;
import com.human.ev_relay_mes.feature.notification.api.NotificationRead;
import com.human.ev_relay_mes.feature.notification.api.NotificationResponseDto;
import com.human.ev_relay_mes.feature.notification.internal.repository.NotificationReadRepository;
import com.human.ev_relay_mes.feature.notification.internal.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService implements NotificationOperations {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 200;

    private final NotificationRepository notificationRepository;
    private final NotificationReadRepository readRepository;
    private final MemberLookup memberLookup;
    private final MaterialInventory materialInventory;
    private final MasterDataLookup masterDataLookup;

    @Override
    public List<NotificationResponseDto> getNotifications(
            Long memberId, Integer limit, String sort, String type, Boolean read) {
        Map<Long, NotificationRead> reads = readsByNotification(memberId);
        Comparator<Notification> comparator = comparator(sort);
        int resultLimit = limit == null
                ? DEFAULT_LIMIT : Math.max(1, Math.min(limit, MAX_LIMIT));
        return notificationRepository.findAllByOrderByOccurredAtDescNotificationIdDesc()
                .stream()
                .filter(notification -> type == null || type.isBlank()
                        || notification.getType().name().equalsIgnoreCase(type))
                .filter(notification -> read == null
                        || read.equals(reads.containsKey(notification.getNotificationId())))
                .sorted(comparator)
                .limit(resultLimit)
                .map(notification -> toResponse(
                        notification, reads.get(notification.getNotificationId())))
                .toList();
    }

    @Override
    public long getUnreadCount(Long memberId) {
        Set<Long> readIds = readsByNotification(memberId).keySet();
        return notificationRepository.count() - readIds.size();
    }

    @Override
    @Transactional
    public NotificationResponseDto markRead(Long notificationId, Long memberId) {
        Notification notification = findNotification(notificationId);
        NotificationRead read = readRepository
                .findByNotification_NotificationIdAndMember_MemberId(
                        notificationId, memberId)
                .orElseGet(() -> {
                    Member member = memberLookup.getRequiredById(memberId);
                    return readRepository.save(NotificationRead.builder()
                            .notification(notification)
                            .member(member)
                            .readAt(LocalDateTime.now())
                            .build());
                });
        return toResponse(notification, read);
    }

    @Override
    @Transactional
    public void markAllRead(Long memberId) {
        Map<Long, NotificationRead> reads = readsByNotification(memberId);
        Member member = memberLookup.getRequiredById(memberId);
        LocalDateTime now = LocalDateTime.now();
        notificationRepository.findAll().stream()
                .filter(notification -> !reads.containsKey(notification.getNotificationId()))
                .forEach(notification -> readRepository.save(NotificationRead.builder()
                        .notification(notification)
                        .member(member)
                        .readAt(now)
                        .build()));
    }

    @Override
    @Transactional
    public void applyMachineAlarmChange(MachineAlarmChangedEvent event) {
        String sourceKey = String.valueOf(event.historyId());
        if (event.cleared()) {
            notificationRepository
                    .findByTypeAndSourceKeyAndResolvedAtIsNullOrderByOccurredAtDesc(
                            Notification.Type.MACHINE_ALARM, sourceKey)
                    .forEach(notification -> notification.setResolvedAt(LocalDateTime.now()));
            return;
        }
        if (notificationRepository.existsByTypeAndSourceKey(
                Notification.Type.MACHINE_ALARM, sourceKey)) {
            return;
        }
        Notification.Severity severity = "ERROR".equalsIgnoreCase(event.alarmLevel())
                ? Notification.Severity.ERROR : Notification.Severity.WARN;
        String detail = event.message() == null || event.message().isBlank()
                ? event.alarmName() : event.message();
        notificationRepository.save(Notification.builder()
                .type(Notification.Type.MACHINE_ALARM)
                .severity(severity)
                .title(event.machineId() + " 설비 " + event.alarmName())
                .message(detail)
                .sourceKey(sourceKey)
                .linkPath("/alarms")
                .occurredAt(event.occurredAt() == null
                        ? LocalDateTime.now() : event.occurredAt())
                .build());
    }

    @Override
    @Transactional
    public void evaluateLowStock(String itemCode) {
        long availableQty = materialInventory.getAvailableQuantity(itemCode);
        List<Notification> active = notificationRepository
                .findByTypeAndSourceKeyAndResolvedAtIsNullOrderByOccurredAtDesc(
                        Notification.Type.LOW_STOCK, itemCode);
        if (availableQty > MaterialStockThreshold.LOW_STOCK) {
            LocalDateTime now = LocalDateTime.now();
            active.forEach(notification -> notification.setResolvedAt(now));
            return;
        }
        if (!active.isEmpty()) {
            return;
        }
        Item item = masterDataLookup.getRequiredItem(itemCode);
        notificationRepository.save(Notification.builder()
                .type(Notification.Type.LOW_STOCK)
                .severity(Notification.Severity.WARN)
                .title(item.getItemName() + " 재고 부족")
                .message(itemCode + " 가용 재고가 " + availableQty
                        + "개로 기준 " + MaterialStockThreshold.LOW_STOCK
                        + "개 이하입니다.")
                .sourceKey(itemCode)
                .linkPath("/materials")
                .occurredAt(LocalDateTime.now())
                .build());
    }

    private Map<Long, NotificationRead> readsByNotification(Long memberId) {
        Map<Long, NotificationRead> reads = new HashMap<>();
        readRepository.findByMember_MemberId(memberId)
                .forEach(read -> reads.put(
                        read.getNotification().getNotificationId(), read));
        return reads;
    }

    private Comparator<Notification> comparator(String sort) {
        Comparator<Notification> latest = Comparator
                .comparing(Notification::getOccurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Notification::getNotificationId,
                        Comparator.nullsLast(Comparator.reverseOrder()));
        if (!"SEVERITY".equalsIgnoreCase(sort)) {
            return latest;
        }
        return Comparator
                .comparingInt((Notification notification) ->
                        severityRank(notification.getSeverity()))
                .thenComparing(latest);
    }

    private int severityRank(Notification.Severity severity) {
        return switch (severity) {
            case ERROR -> 0;
            case WARN -> 1;
            case INFO -> 2;
        };
    }

    private Notification findNotification(Long notificationId) {
        return notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.NOTIFICATION_NOT_FOUND));
    }

    private NotificationResponseDto toResponse(
            Notification notification, NotificationRead read) {
        return NotificationResponseDto.builder()
                .notificationId(notification.getNotificationId())
                .type(notification.getType().name())
                .severity(notification.getSeverity().name())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .sourceKey(notification.getSourceKey())
                .linkPath(notification.getLinkPath())
                .occurredAt(notification.getOccurredAt())
                .resolvedAt(notification.getResolvedAt())
                .read(read != null)
                .readAt(read == null ? null : read.getReadAt())
                .build();
    }
}
