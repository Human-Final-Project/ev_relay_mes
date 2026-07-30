package com.human.ev_relay_mes.feature.notification.internal.service;

import com.human.ev_relay_mes.feature.machine.api.MachineAlarmChangedEvent;
import com.human.ev_relay_mes.feature.material.api.MaterialStockChangedEvent;
import com.human.ev_relay_mes.feature.notification.api.NotificationOperations;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventHandler {

    private final NotificationOperations notificationOperations;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void handleMachineAlarm(MachineAlarmChangedEvent event) {
        try {
            notificationOperations.applyMachineAlarmChange(event);
        } catch (RuntimeException exception) {
            log.error("설비 알림 생성/해결 실패 historyId={}",
                    event.historyId(), exception);
        }
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    public void handleMaterialStock(MaterialStockChangedEvent event) {
        try {
            notificationOperations.evaluateLowStock(event.itemCode());
        } catch (RuntimeException exception) {
            log.error("재고 부족 알림 평가 실패 itemCode={}",
                    event.itemCode(), exception);
        }
    }
}
