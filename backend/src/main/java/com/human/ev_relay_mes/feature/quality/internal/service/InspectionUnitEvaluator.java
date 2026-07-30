package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.InspectionStandardOperations;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.ProductionOperations;
import com.human.ev_relay_mes.feature.quality.api.Inspection;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResult;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionRepository;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionUnitResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
class InspectionUnitEvaluator {

    private final InspectionRepository inspectionRepository;
    private final InspectionUnitResultRepository unitResultRepository;
    private final InspectionStandardOperations inspectionStandardOperations;
    private final ProductionOperations productionOperations;

    InspectionUnitResult evaluate(
            Lot lot, Machine machine, Process process,
            Integer unitSeq, int expectedInputQty) {
        long requiredItemCount = requiredMeasurementCount(lot, process);
        long receivedItemCount = inspectionRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                        lot.getLotNo(), process.getProcessCode(), unitSeq);
        InspectionUnitResult unitResult = findOrCreate(
                lot, machine, process, unitSeq);

        if (receivedItemCount >= requiredItemCount) {
            unitResult.setMeasurementResult(measurementResult(
                    lot.getLotNo(), process.getProcessCode(), unitSeq));
        }
        if (unitResult.getL1Result() == null
                || unitResult.getMeasurementResult() == null) {
            return unitResultRepository.save(unitResult);
        }

        completeUnitEvaluation(unitResult);
        unitResultRepository.save(unitResult);
        completeProcessIfReady(lot, machine, process, expectedInputQty);
        return unitResult;
    }

    private long requiredMeasurementCount(Lot lot, Process process) {
        long count = inspectionStandardOperations.snapshotCount(lot, process);
        if (count == 0
                && inspectionStandardOperations.supportsMeasurements(
                process.getProcessCode())) {
            inspectionStandardOperations.captureStandardsIfAbsent(lot, process);
            return inspectionStandardOperations.snapshotCount(lot, process);
        }
        return count;
    }

    private InspectionUnitResult findOrCreate(
            Lot lot, Machine machine, Process process, Integer unitSeq) {
        return unitResultRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                        lot.getLotNo(), process.getProcessCode(), unitSeq)
                .orElseGet(() -> unitResultRepository.save(
                        InspectionUnitResult.builder()
                                .lot(lot)
                                .machine(machine)
                                .process(process)
                                .unitSeq(unitSeq)
                                .build()));
    }

    private Inspection.Result measurementResult(
            String lotNo, String processCode, Integer unitSeq) {
        List<Inspection> inspections = inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqOrderByInspectionIdAsc(
                        lotNo, processCode, unitSeq);
        return inspections.stream()
                .anyMatch(item -> item.getResult() == Inspection.Result.NG)
                ? Inspection.Result.NG : Inspection.Result.OK;
    }

    private void completeUnitEvaluation(InspectionUnitResult unitResult) {
        unitResult.setResult(
                unitResult.getL1Result() == Inspection.Result.OK
                        && unitResult.getMeasurementResult() == Inspection.Result.OK
                        ? Inspection.Result.OK : Inspection.Result.NG);
        unitResult.setEvaluationStatus(
                InspectionUnitResult.EvaluationStatus.COMPLETED);
        unitResult.setEvaluatedAt(LocalDateTime.now());
    }

    private void completeProcessIfReady(
            Lot lot, Machine machine, Process process, int expectedInputQty) {
        long completedUnits = unitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
                        lot.getLotNo(),
                        process.getProcessCode(),
                        InspectionUnitResult.EvaluationStatus.COMPLETED);
        if (completedUnits < expectedInputQty) {
            return;
        }
        if (completedUnits > expectedInputQty) {
            throw new CustomException(
                    ErrorCode.INVALID_INSPECTION_UNIT_SEQ,
                    "완료된 검사 제품 수가 공정 투입수량을 초과했습니다.");
        }

        int okQty = resultCount(lot, process, Inspection.Result.OK);
        int ngQty = resultCount(lot, process, Inspection.Result.NG);
        productionOperations.completeEvaluatedProcess(
                lot, machine, process, expectedInputQty, okQty, ngQty);
    }

    private int resultCount(Lot lot, Process process, Inspection.Result result) {
        return Math.toIntExact(unitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndResult(
                        lot.getLotNo(), process.getProcessCode(), result));
    }
}
