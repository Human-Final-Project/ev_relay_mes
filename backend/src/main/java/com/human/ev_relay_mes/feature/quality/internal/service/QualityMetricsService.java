package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.feature.quality.api.Inspection;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResult;
import com.human.ev_relay_mes.feature.quality.api.QualityMetrics;
import com.human.ev_relay_mes.feature.quality.internal.repository.DefectHistoryRepository;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionRepository;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionUnitResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualityMetricsService implements QualityMetrics {

    private final InspectionRepository inspectionRepository;
    private final InspectionUnitResultRepository unitResultRepository;
    private final DefectHistoryRepository defectHistoryRepository;

    @Override
    public long countCompletedUnits(String lotNo, String processCode) {
        return unitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
                        lotNo,
                        processCode,
                        InspectionUnitResult.EvaluationStatus.COMPLETED);
    }

    @Override
    public QualityPeriodSummary summarizePeriod(LocalDateTime startAt, LocalDateTime endAt) {
        List<Inspection> inspections = inspectionRepository
                .findByInspectedAtBetweenOrderByInspectedAtDesc(startAt, endAt);
        int defectQty = defectHistoryRepository
                .findByOccurredAtBetweenOrderByOccurredAtDesc(startAt, endAt)
                .stream()
                .mapToInt(defect -> valueOrZero(defect.getDefectQty()))
                .sum();
        return new QualityPeriodSummary(
                inspections.stream().filter(item -> item.getResult() == Inspection.Result.OK).count(),
                inspections.stream().filter(item -> item.getResult() == Inspection.Result.NG).count(),
                defectQty);
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
