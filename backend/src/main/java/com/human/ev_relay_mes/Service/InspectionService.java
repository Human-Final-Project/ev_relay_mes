package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionSearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.UnitJudgmentReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Response.InspectionResponseDto;
import com.human.ev_relay_mes.Dto.Response.InspectionUnitResultResponseDto;
import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.InspectionUnitResult;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.LotInspectionStandardSnapshot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.InspectionRepository;
import com.human.ev_relay_mes.Repository.InspectionUnitResultRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final InspectionUnitResultRepository unitResultRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final LotRepository lotRepository;
    private final InspectionStandardService inspectionStandardService;
    private final ProductionService productionService;
    private final DefectService defectService;

    private static final Map<String, String> MEASUREMENT_DEFECT_CODES = Map.ofEntries(
            Map.entry("OP20:COIL_RESISTANCE", "COIL_RESISTANCE_NG"),
            Map.entry("OP30:WELD_STRENGTH", "WELD_STRENGTH_NG"),
            Map.entry("OP30:CONTACT_RESISTANCE", "CONTACT_RESISTANCE_NG"),
            Map.entry("OP30:CONTACT_POSITION", "CONTACT_POSITION_NG"),
            Map.entry("OP60:GAS_PRESSURE", "GAS_PRESSURE_NG"),
            Map.entry("OP60:LEAK_RATE", "SEAL_LEAK_NG"),
            Map.entry("OP70:INSULATION_RESISTANCE", "INSULATION_NG"),
            Map.entry("OP70:WITHSTAND_VOLTAGE", "WITHSTAND_VOLTAGE_NG"),
            Map.entry("OP70:OPERATION_VOLTAGE", "OPERATION_VOLTAGE_NG"),
            Map.entry("OP70:CONTACT_BOUNCE", "CONTACT_BOUNCE_NG"));

    @Transactional
    public InspectionResponseDto saveResult(InspectionResultReceiveRequestDto dto) {
        String eventId = normalizeEventId(dto.getEventId());
        if (eventId != null) {
            Optional<Inspection> existingByEvent = inspectionRepository.findByEventId(eventId);
            if (existingByEvent.isPresent()) {
                return toResponse(existingByEvent.get());
            }
        }

        Lot lot = lotRepository.findByLotNoForUpdate(dto.getLotNo())
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));

        validateLot(lot, process);
        validateMachineAndProcess(machine, process);
        int expectedInputQty = productionService.expectedInputQtyFor(lot, process);
        if (dto.getUnitSeq() > expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_UNIT_SEQ,
                    "검사 순번이 OP70 투입수량을 초과합니다.");
        }

        LotInspectionStandardSnapshot snapshot = inspectionStandardService
                .resolveSnapshot(lot, process, dto.getInspectionItem());
        validateUnit(dto.getUnit(), snapshot.getUnit());

        Optional<Inspection> existingMeasurement = inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqAndInspectionItem(
                        lot.getLotNo(), process.getProcessCode(),
                        dto.getUnitSeq(), snapshot.getInspectionItem());
        if (existingMeasurement.isPresent()) {
            Inspection existing = existingMeasurement.get();
            if (existing.getMeasuredValue().compareTo(dto.getMeasuredValue()) != 0) {
                throw new CustomException(ErrorCode.DUPLICATE_INSPECTION_MEASUREMENT,
                        "같은 제품 순번과 검사 항목에 다른 측정값이 이미 등록되어 있습니다.");
            }
            return toResponse(existing);
        }

        Inspection.Result result = judge(
                dto.getMeasuredValue(), snapshot.getLowerLimit(), snapshot.getUpperLimit());
        Inspection inspection = Inspection.builder()
                .eventId(eventId)
                .lot(lot)
                .machine(machine)
                .process(process)
                .standardSnapshot(snapshot)
                .unitSeq(dto.getUnitSeq())
                .inspectionItem(snapshot.getInspectionItem())
                .measuredValue(dto.getMeasuredValue())
                .unit(snapshot.getUnit())
                .result(result)
                .build();
        Inspection saved = inspectionRepository.save(inspection);

        if (result == Inspection.Result.NG) {
            createDefect(lot, machine, process, dto.getUnitSeq(),
                    measurementDefectCode(process.getProcessCode(), snapshot.getInspectionItem()),
                    "측정값 기준 이탈: " + snapshot.getInspectionItem());
        }

        evaluateUnitAndCompleteProcessIfReady(
                lot, machine, process, dto.getUnitSeq(), expectedInputQty);
        return toResponse(saved);
    }

    @Transactional
    public InspectionUnitResultResponseDto saveJudgment(UnitJudgmentReceiveRequestDto dto) {
        Lot lot = lotRepository.findByLotNoForUpdate(dto.getLotNo())
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        validateLot(lot, process);
        validateMachineAndProcess(machine, process);
        int expectedInputQty = productionService.expectedInputQtyFor(lot, process);
        if (dto.getUnitSeq() > expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_UNIT_SEQ);
        }

        Inspection.Result l1Result;
        try {
            l1Result = Inspection.Result.valueOf(dto.getResult().trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_RESULT);
        }
        if (l1Result == Inspection.Result.NG && isBlank(dto.getDefectCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "L1 NG 판정에는 불량 코드가 필요합니다.");
        }

        InspectionUnitResult unitResult = unitResultRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                        lot.getLotNo(), process.getProcessCode(), dto.getUnitSeq())
                .orElseGet(() -> InspectionUnitResult.builder()
                        .lot(lot).machine(machine).process(process)
                        .unitSeq(dto.getUnitSeq()).build());
        if (unitResult.getL1Result() != null && unitResult.getL1Result() != l1Result) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE,
                    "동일 제품 순번에 다른 L1 판정이 이미 등록되어 있습니다.");
        }
        unitResult.setL1Result(l1Result);
        unitResultRepository.save(unitResult);

        if (l1Result == Inspection.Result.NG) {
            createDefect(lot, machine, process, dto.getUnitSeq(),
                    dto.getDefectCode().trim().toUpperCase(), dto.getMessage());
        }
        InspectionUnitResult evaluated = evaluateUnitAndCompleteProcessIfReady(
                lot, machine, process, dto.getUnitSeq(), expectedInputQty);
        return toUnitResponse(evaluated);
    }

    public List<InspectionResponseDto> search(InspectionSearchRequestDto condition) {
        validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return inspectionRepository.findAll(Sort.by(Sort.Direction.DESC, "inspectedAt")).stream()
                .filter(item -> isBlank(condition.getLotNo())
                        || item.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(item -> isBlank(condition.getMachineId())
                        || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> isBlank(condition.getProcessCode())
                        || item.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(item -> isBlank(condition.getResult())
                        || item.getResult().name().equalsIgnoreCase(condition.getResult()))
                .filter(item -> isWithin(item.getInspectedAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    private InspectionUnitResult evaluateUnitAndCompleteProcessIfReady(
            Lot lot, Machine machine, Process process, Integer unitSeq, int expectedInputQty) {
        long requiredItemCount = inspectionStandardService.snapshotCount(lot, process);
        if (requiredItemCount == 0 && InspectionStandardService.supportsMeasurements(process.getProcessCode())) {
            inspectionStandardService.captureStandardsIfAbsent(lot, process);
            requiredItemCount = inspectionStandardService.snapshotCount(lot, process);
        }
        long receivedItemCount = inspectionRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                        lot.getLotNo(), process.getProcessCode(), unitSeq);
        InspectionUnitResult unitResult = unitResultRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                        lot.getLotNo(), process.getProcessCode(), unitSeq)
                .orElseGet(() -> unitResultRepository.save(InspectionUnitResult.builder()
                        .lot(lot).machine(machine).process(process).unitSeq(unitSeq).build()));

        if (receivedItemCount >= requiredItemCount) {
            List<Inspection> unitInspections = inspectionRepository
                    .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqOrderByInspectionIdAsc(
                            lot.getLotNo(), process.getProcessCode(), unitSeq);
            unitResult.setMeasurementResult(unitInspections.stream()
                    .anyMatch(item -> item.getResult() == Inspection.Result.NG)
                    ? Inspection.Result.NG : Inspection.Result.OK);
        }
        if (unitResult.getL1Result() == null || unitResult.getMeasurementResult() == null) {
            return unitResultRepository.save(unitResult);
        }
        unitResult.setResult(unitResult.getL1Result() == Inspection.Result.OK
                        && unitResult.getMeasurementResult() == Inspection.Result.OK
                ? Inspection.Result.OK : Inspection.Result.NG);
        unitResult.setEvaluationStatus(InspectionUnitResult.EvaluationStatus.COMPLETED);
        unitResult.setEvaluatedAt(LocalDateTime.now());
        unitResultRepository.save(unitResult);

        long completedUnits = unitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
                        lot.getLotNo(), process.getProcessCode(),
                        InspectionUnitResult.EvaluationStatus.COMPLETED);
        if (completedUnits < expectedInputQty) {
            return unitResult;
        }
        if (completedUnits > expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_UNIT_SEQ,
                    "완료된 검사 제품 수가 OP70 투입수량을 초과했습니다.");
        }

        int okQty = Math.toIntExact(unitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndResult(
                        lot.getLotNo(), process.getProcessCode(), Inspection.Result.OK));
        int ngQty = Math.toIntExact(unitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndResult(
                        lot.getLotNo(), process.getProcessCode(), Inspection.Result.NG));
        productionService.completeEvaluatedProcess(
                lot, machine, process, expectedInputQty, okQty, ngQty);
        return unitResult;
    }

    private Inspection.Result judge(
            java.math.BigDecimal value,
            java.math.BigDecimal lower,
            java.math.BigDecimal upper) {
        boolean below = lower != null && value.compareTo(lower) < 0;
        boolean above = upper != null && value.compareTo(upper) > 0;
        return below || above ? Inspection.Result.NG : Inspection.Result.OK;
    }

    private void validateLot(Lot lot, Process process) {
        if (lot.getStatus() != Lot.Status.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS,
                    "생산 중인 LOT에만 검사 측정값을 등록할 수 있습니다.");
        }
        if (lot.getCurrentProcess() == null
                || !isProcessReady(lot, process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER,
                    "LOT의 현재 공정과 판정 공정이 일치하지 않습니다.");
        }
    }

    private boolean isProcessReady(Lot lot, String processCode) {
        String current = lot.getCurrentProcess().getProcessCode();
        return current.equals(processCode)
                || ("OP20".equals(current) && "OP30".equals(processCode));
    }

    private String measurementDefectCode(String processCode, String inspectionItem) {
        String code = MEASUREMENT_DEFECT_CODES.get(processCode + ":" + inspectionItem);
        if (code == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "검사 항목에 연결된 불량 코드가 없습니다.");
        }
        return code;
    }

    private void createDefect(Lot lot, Machine machine, Process process,
                              Integer unitSeq, String defectCode, String message) {
        DefectHistoryCreateRequestDto defect = new DefectHistoryCreateRequestDto();
        defect.setEventId("AUTO-" + lot.getLotNo() + "-" + process.getProcessCode()
                + "-" + unitSeq + "-" + defectCode);
        defect.setLotNo(lot.getLotNo());
        defect.setMachineId(machine.getMachineId());
        defect.setProcessCode(process.getProcessCode());
        defect.setDefectCode(defectCode);
        defect.setDefectQty(1);
        defect.setMessage(message);
        defectService.createDefect(defect);
    }

    private void validateMachineAndProcess(Machine machine, Process process) {
        if (!machine.getProcess().getProcessCode().equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "설비에 지정된 공정과 검사 공정이 일치하지 않습니다.");
        }
    }

    private void validateUnit(String receivedUnit, String standardUnit) {
        if (!isBlank(receivedUnit) && !receivedUnit.equalsIgnoreCase(standardUnit)) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_VALUE,
                    "측정 단위가 검사 기준 단위와 일치하지 않습니다.");
        }
    }

    private void validateSearchPeriod(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "조회 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEventId(String eventId) {
        return isBlank(eventId) ? null : eventId.trim();
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start))
                && (end == null || !value.isAfter(end));
    }

    private InspectionResponseDto toResponse(Inspection inspection) {
        return InspectionResponseDto.builder()
                .inspectionId(inspection.getInspectionId())
                .lotNo(inspection.getLot().getLotNo())
                .machineId(inspection.getMachine().getMachineId())
                .machineName(inspection.getMachine().getMachineName())
                .processCode(inspection.getProcess().getProcessCode())
                .processName(inspection.getProcess().getProcessName())
                .unitSeq(inspection.getUnitSeq())
                .inspectionItem(inspection.getInspectionItem())
                .measuredValue(inspection.getMeasuredValue())
                .unit(inspection.getUnit())
                .lowerLimit(inspection.getStandardSnapshot().getLowerLimit())
                .upperLimit(inspection.getStandardSnapshot().getUpperLimit())
                .standardVersion(inspection.getStandardSnapshot().getStandardVersion())
                .result(inspection.getResult().name())
                .inspectedAt(inspection.getInspectedAt())
                .build();
    }

    private InspectionUnitResultResponseDto toUnitResponse(InspectionUnitResult result) {
        return InspectionUnitResultResponseDto.builder()
                .lotNo(result.getLot().getLotNo())
                .machineId(result.getMachine().getMachineId())
                .processCode(result.getProcess().getProcessCode())
                .unitSeq(result.getUnitSeq())
                .l1Result(result.getL1Result() == null ? null : result.getL1Result().name())
                .measurementResult(result.getMeasurementResult() == null
                        ? null : result.getMeasurementResult().name())
                .result(result.getResult() == null ? null : result.getResult().name())
                .evaluationStatus(result.getEvaluationStatus().name())
                .build();
    }
}
