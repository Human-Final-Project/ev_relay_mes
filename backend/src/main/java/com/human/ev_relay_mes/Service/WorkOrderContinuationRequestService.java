package com.human.ev_relay_mes.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/** 최종 공정 커밋 이후 작업지시 완료 또는 자동 보충 여부를 평가하도록 요청한다. */
@Service
@RequiredArgsConstructor
public class WorkOrderContinuationRequestService {

    private final ApplicationEventPublisher eventPublisher;

    public void requestEvaluation(Long workOrderId) {
        eventPublisher.publishEvent(new WorkOrderEvaluationRequested(workOrderId));
    }

    public record WorkOrderEvaluationRequested(Long workOrderId) {
    }
}
