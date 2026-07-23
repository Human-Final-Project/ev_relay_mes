package com.human.ev_relay_mes.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 상태 이벤트가 유실되거나 서버가 재시작된 경우에도 RUNNING LOT 대기열을
 * 주기적으로 다시 확인해 빈 설비에 배정한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "mes.pipeline.reconcile-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProductionPipelineReconciler {

    private final ProductionSchedulerService productionSchedulerService;

    @Scheduled(
            initialDelayString = "${mes.pipeline.reconcile-initial-delay-ms:3000}",
            fixedDelayString = "${mes.pipeline.reconcile-delay-ms:3000}")
    public void reconcile() {
        try {
            productionSchedulerService.tryAssignAllIdleMachines();
        } catch (RuntimeException exception) {
            log.error("파이프라인 정합성 재확인 실패", exception);
        }
    }
}
