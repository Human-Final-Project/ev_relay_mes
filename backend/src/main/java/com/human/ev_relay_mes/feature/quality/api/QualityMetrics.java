package com.human.ev_relay_mes.feature.quality.api;

import java.time.LocalDateTime;

/**
 * 생산·설비·대시보드에서 품질 집계값만 조회하는 공개 계약입니다.
 */
public interface QualityMetrics {

    long countCompletedUnits(String lotNo, String processCode);

    QualityPeriodSummary summarizePeriod(LocalDateTime startAt, LocalDateTime endAt);

    record QualityPeriodSummary(long okInspections, long ngInspections, int defectQty) {
    }
}
