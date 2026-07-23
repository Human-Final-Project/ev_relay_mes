package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 이벤트 유실이나 서버 재시작 뒤에도 미완료 WorkOrder의 보충 생산을 복구한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "mes.auto-lot.reconcile-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class WorkOrderContinuationReconciler {

    private final WorkOrderRepository workOrderRepository;
    private final AutoSupplementLotService autoSupplementLotService;
    private final WorkOrderService workOrderService;

    @Scheduled(
            initialDelayString = "${mes.auto-lot.reconcile-initial-delay-ms:5000}",
            fixedDelayString = "${mes.auto-lot.reconcile-delay-ms:5000}")
    public void reconcile() {
        for (WorkOrder workOrder : workOrderRepository
                .findByStatusOrderByCreatedAtDesc(WorkOrder.Status.RELEASED)) {
            try {
                workOrderService.releaseAndStart(workOrder.getWorkOrderId(), null);
            } catch (RuntimeException exception) {
                log.error("최초 LOT 자동 생성 정합성 확인 실패 workOrderId={}",
                        workOrder.getWorkOrderId(), exception);
            }
        }
        for (WorkOrder workOrder : workOrderRepository
                .findByStatusOrderByCreatedAtDesc(WorkOrder.Status.RUNNING)) {
            try {
                autoSupplementLotService.evaluateAndContinue(workOrder.getWorkOrderId());
            } catch (RuntimeException exception) {
                log.error("자동 보충 정합성 확인 실패 workOrderId={}",
                        workOrder.getWorkOrderId(), exception);
            }
        }
    }
}
