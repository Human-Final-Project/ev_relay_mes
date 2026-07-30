package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.masterdata.api.InspectionStandardOperations;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.LotInspectionStandardSnapshot;
import com.human.ev_relay_mes.feature.production.api.ProductionData;
import com.human.ev_relay_mes.feature.production.api.ProductionOperations;
import com.human.ev_relay_mes.feature.quality.api.Inspection;
import com.human.ev_relay_mes.feature.quality.api.InspectionOperations;
import com.human.ev_relay_mes.feature.quality.api.InspectionResponseDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionSearchRequestDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResult;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResultResponseDto;
import com.human.ev_relay_mes.feature.quality.api.UnitJudgmentReceiveRequestDto;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionRepository;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionUnitResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionService implements InspectionOperations {

    private final InspectionRepository inspectionRepository;
    private final InspectionUnitResultRepository unitResultRepository;
    private final MachineRegistry machineRegistry;
    private final MasterDataLookup masterDataLookup;
    private final ProductionData productionData;
    private final InspectionStandardOperations inspectionStandardOperations;
    private final ProductionOperations productionOperations;
    private final InspectionRules rules;
    private final InspectionDefectRecorder defectRecorder;
    private final InspectionUnitEvaluator unitEvaluator;
    private final InspectionResponseAssembler responseAssembler;

    @Transactional
    @Override
    public InspectionResponseDto saveResult(InspectionResultReceiveRequestDto request) {
        String eventId = RequestValues.trimToNull(request.getEventId());
        if (eventId != null) {
            Optional<Inspection> existing = inspectionRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return responseAssembler.toResponse(existing.get());
            }
        }

        InspectionContext context = loadContext(
                request.getLotNo(), request.getMachineId(), request.getProcessCode());
        int expectedInputQty = productionOperations.expectedInputQtyFor(
                context.lot(), context.process());
        rules.validateUnitSequence(request.getUnitSeq(), expectedInputQty);

        LotInspectionStandardSnapshot snapshot = inspectionStandardOperations
                .resolveSnapshot(
                        context.lot(), context.process(), request.getInspectionItem());
        rules.validateUnit(request.getUnit(), snapshot.getUnit());

        Optional<Inspection> existingMeasurement = inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqAndInspectionItem(
                        context.lot().getLotNo(),
                        context.process().getProcessCode(),
                        request.getUnitSeq(),
                        snapshot.getInspectionItem());
        if (existingMeasurement.isPresent()) {
            return handleExistingMeasurement(
                    existingMeasurement.get(), request);
        }

        Inspection.Result result = rules.judge(
                request.getMeasuredValue(),
                snapshot.getLowerLimit(),
                snapshot.getUpperLimit());
        Inspection saved = inspectionRepository.save(Inspection.builder()
                .eventId(eventId)
                .lot(context.lot())
                .machine(context.machine())
                .process(context.process())
                .standardSnapshot(snapshot)
                .unitSeq(request.getUnitSeq())
                .inspectionItem(snapshot.getInspectionItem())
                .measuredValue(request.getMeasuredValue())
                .unit(snapshot.getUnit())
                .result(result)
                .build());

        if (result == Inspection.Result.NG) {
            defectRecorder.record(
                    context.lot(),
                    context.machine(),
                    context.process(),
                    request.getUnitSeq(),
                    rules.measurementDefectCode(
                            context.process().getProcessCode(),
                            snapshot.getInspectionItem()),
                    "측정값 기준 이탈: " + snapshot.getInspectionItem());
        }

        unitEvaluator.evaluate(
                context.lot(),
                context.machine(),
                context.process(),
                request.getUnitSeq(),
                expectedInputQty);
        return responseAssembler.toResponse(saved);
    }

    @Transactional
    @Override
    public InspectionUnitResultResponseDto saveJudgment(
            UnitJudgmentReceiveRequestDto request) {
        InspectionContext context = loadContext(
                request.getLotNo(), request.getMachineId(), request.getProcessCode());
        int expectedInputQty = productionOperations.expectedInputQtyFor(
                context.lot(), context.process());
        rules.validateUnitSequence(request.getUnitSeq(), expectedInputQty);

        Inspection.Result l1Result = rules.parseJudgment(request.getResult());
        if (l1Result == Inspection.Result.NG
                && RequestValues.isBlank(request.getDefectCode())) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "L1 NG 판정에는 불량 코드가 필요합니다.");
        }

        InspectionUnitResult unitResult = findOrCreateUnitResult(
                context, request.getUnitSeq());
        if (unitResult.getL1Result() != null
                && unitResult.getL1Result() != l1Result) {
            throw new CustomException(
                    ErrorCode.DUPLICATE_RESOURCE,
                    "동일 제품 일련번호에 다른 L1 판정이 이미 등록되어 있습니다.");
        }
        unitResult.setL1Result(l1Result);
        unitResultRepository.save(unitResult);

        if (l1Result == Inspection.Result.NG) {
            defectRecorder.record(
                    context.lot(),
                    context.machine(),
                    context.process(),
                    request.getUnitSeq(),
                    request.getDefectCode().trim().toUpperCase(),
                    request.getMessage());
        }
        InspectionUnitResult evaluated = unitEvaluator.evaluate(
                context.lot(),
                context.machine(),
                context.process(),
                request.getUnitSeq(),
                expectedInputQty);
        return responseAssembler.toUnitResponse(evaluated);
    }

    @Override
    public List<InspectionResponseDto> search(InspectionSearchRequestDto condition) {
        RequestValues.validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return inspectionRepository
                .findAll(Sort.by(Sort.Direction.DESC, "inspectedAt"))
                .stream()
                .filter(item -> condition.getWorkOrderId() == null
                        || item.getLot().getWorkOrder().getWorkOrderId()
                        .equals(condition.getWorkOrderId()))
                .filter(item -> RequestValues.isBlank(condition.getLotNo())
                        || item.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(item -> RequestValues.isBlank(condition.getMachineId())
                        || item.getMachine().getMachineId()
                        .equals(condition.getMachineId()))
                .filter(item -> RequestValues.isBlank(condition.getProcessCode())
                        || item.getProcess().getProcessCode()
                        .equals(condition.getProcessCode()))
                .filter(item -> RequestValues.isBlank(condition.getResult())
                        || item.getResult().name()
                        .equalsIgnoreCase(condition.getResult()))
                .filter(item -> RequestValues.isWithinInclusive(
                        item.getInspectedAt(),
                        condition.getStartAt(),
                        condition.getEndAt()))
                .map(responseAssembler::toResponse)
                .toList();
    }

    private InspectionContext loadContext(
            String lotNo, String machineId, String processCode) {
        Lot lot = productionData.getRequiredLotForUpdate(lotNo);
        Machine machine = machineRegistry.getRequiredMachine(machineId);
        Process process = masterDataLookup.getRequiredProcess(processCode);
        rules.validateLot(lot, process);
        rules.validateMachineProcess(machine, process);
        return new InspectionContext(lot, machine, process);
    }

    private InspectionResponseDto handleExistingMeasurement(
            Inspection existing,
            InspectionResultReceiveRequestDto request) {
        if (existing.getMeasuredValue().compareTo(request.getMeasuredValue()) != 0) {
            throw new CustomException(
                    ErrorCode.DUPLICATE_INSPECTION_MEASUREMENT,
                    "같은 제품 일련번호와 검사 항목에 다른 측정값이 이미 등록되어 있습니다.");
        }
        return responseAssembler.toResponse(existing);
    }

    private InspectionUnitResult findOrCreateUnitResult(
            InspectionContext context, Integer unitSeq) {
        return unitResultRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                        context.lot().getLotNo(),
                        context.process().getProcessCode(),
                        unitSeq)
                .orElseGet(() -> InspectionUnitResult.builder()
                        .lot(context.lot())
                        .machine(context.machine())
                        .process(context.process())
                        .unitSeq(unitSeq)
                        .build());
    }

    private record InspectionContext(Lot lot, Machine machine, Process process) {
    }
}
