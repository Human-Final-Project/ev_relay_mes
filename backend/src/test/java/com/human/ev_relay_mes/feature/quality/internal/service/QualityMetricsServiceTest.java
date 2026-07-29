package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.feature.quality.api.DefectHistory;
import com.human.ev_relay_mes.feature.quality.api.Inspection;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResult;
import com.human.ev_relay_mes.feature.quality.internal.repository.DefectHistoryRepository;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionRepository;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionUnitResultRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QualityMetricsServiceTest {

    @Mock InspectionRepository inspectionRepository;
    @Mock InspectionUnitResultRepository unitResultRepository;
    @Mock DefectHistoryRepository defectHistoryRepository;

    @InjectMocks QualityMetricsService qualityMetricsService;

    @Test
    void countsCompletedUnitsThroughTheQualityBoundary() {
        when(unitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
                        "LOT-001", "OP70",
                        InspectionUnitResult.EvaluationStatus.COMPLETED))
                .thenReturn(7L);

        assertThat(qualityMetricsService.countCompletedUnits("LOT-001", "OP70"))
                .isEqualTo(7L);
    }

    @Test
    void summarizesInspectionResultsAndDefectQuantityForPeriod() {
        LocalDateTime startAt = LocalDateTime.of(2026, 7, 29, 0, 0);
        LocalDateTime endAt = startAt.plusDays(1);
        when(inspectionRepository.findByInspectedAtBetweenOrderByInspectedAtDesc(startAt, endAt))
                .thenReturn(List.of(
                        Inspection.builder().result(Inspection.Result.OK).build(),
                        Inspection.builder().result(Inspection.Result.OK).build(),
                        Inspection.builder().result(Inspection.Result.NG).build()));
        when(defectHistoryRepository
                .findByOccurredAtBetweenOrderByOccurredAtDesc(startAt, endAt))
                .thenReturn(List.of(
                        DefectHistory.builder().defectQty(2).build(),
                        DefectHistory.builder().defectQty(3).build()));

        var summary = qualityMetricsService.summarizePeriod(startAt, endAt);

        assertThat(summary.okInspections()).isEqualTo(2);
        assertThat(summary.ngInspections()).isEqualTo(1);
        assertThat(summary.defectQty()).isEqualTo(5);
        verify(inspectionRepository)
                .findByInspectedAtBetweenOrderByInspectedAtDesc(startAt, endAt);
    }
}
