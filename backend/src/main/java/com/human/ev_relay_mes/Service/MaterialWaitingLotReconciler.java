package com.human.ev_relay_mes.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 자재 입고 이벤트를 놓쳐도 WAITING LOT의 자동 투입을 주기적으로 재확인한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "mes.auto-lot.material-reconcile-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class MaterialWaitingLotReconciler {

    private final LotService lotService;

    @Scheduled(
            initialDelayString = "${mes.auto-lot.material-reconcile-initial-delay-ms:5000}",
            fixedDelayString = "${mes.auto-lot.material-reconcile-delay-ms:5000}")
    public void reconcile() {
        try {
            lotService.retryMaterialWaitingLots();
        } catch (RuntimeException exception) {
            log.error("자재 대기 LOT 자동 투입 재확인 실패", exception);
        }
    }
}
