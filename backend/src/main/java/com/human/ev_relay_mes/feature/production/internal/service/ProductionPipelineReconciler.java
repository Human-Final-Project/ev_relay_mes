package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Service.WorkCommandService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 일회성 스케줄 이벤트가 실패하더라도 유령 명령을 정리하고 IDLE 설비를 다시 배정한다.
 * 정상 흐름은 트랜잭션 이벤트가 즉시 처리하며, 이 작업은 복구 안전망으로만 동작한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "mes.pipeline.reconcile-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProductionPipelineReconciler {

    private final WorkCommandService workCommandService;
    private final ProductionSchedulerService productionSchedulerService;

    @Scheduled(
            initialDelayString = "${mes.pipeline.reconcile-initial-delay-ms:3000}",
            fixedDelayString = "${mes.pipeline.reconcile-delay-ms:3000}")
    public void reconcile() {
        try {
            workCommandService.cancelActiveCommandsForTerminalLots();
        } catch (RuntimeException exception) {
            log.error("종료 LOT 유령 작업명령 정리 실패", exception);
            return;
        }

        try {
            productionSchedulerService.tryAssignAllIdleMachines();
        } catch (RuntimeException exception) {
            log.error("IDLE 설비 파이프라인 재배정 실패", exception);
        }
    }
}
