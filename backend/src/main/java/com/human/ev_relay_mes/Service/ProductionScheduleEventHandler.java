package com.human.ev_relay_mes.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/** 커밋된 생산 상태를 기준으로 새 트랜잭션에서 파이프라인을 재배정한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductionScheduleEventHandler {

    private final ProductionSchedulerService productionSchedulerService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(ProductionScheduleRequestService.ScheduleRequested event) {
        try {
            switch (event.target()) {
                case LOT -> productionSchedulerService.tryScheduleLot(event.key());
                case MACHINE -> productionSchedulerService.tryAssignMachine(event.key());
                case ALL_IDLE_MACHINES -> productionSchedulerService.tryAssignAllIdleMachines();
            }
        } catch (RuntimeException exception) {
            // 스케줄러 트랜잭션의 일부 명령만 커밋되지 않도록 전체를 롤백한다.
            // 원 생산 트랜잭션은 이미 커밋되었으므로 HTTP 응답 상태는 뒤집지 않는다.
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("파이프라인 재배정 실패 target={}, key={}",
                    event.target(), event.key(), exception);
        }
    }
}
