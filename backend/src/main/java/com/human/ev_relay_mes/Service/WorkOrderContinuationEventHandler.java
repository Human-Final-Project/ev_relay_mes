package com.human.ev_relay_mes.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

/** 최종 LOT 커밋 후 별도 트랜잭션에서 완료 또는 보충 LOT 생성을 처리한다. */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkOrderContinuationEventHandler {

    private final AutoSupplementLotService autoSupplementLotService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(WorkOrderContinuationRequestService.WorkOrderEvaluationRequested event) {
        try {
            autoSupplementLotService.evaluateAndContinue(event.workOrderId());
        } catch (RuntimeException exception) {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            log.error("작업지시 자동 완료/보충 평가 실패 workOrderId={}",
                    event.workOrderId(), exception);
        }
    }
}
