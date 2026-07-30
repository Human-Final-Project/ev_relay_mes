package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.api.ProcessCodes;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.ProductionLog;
import com.human.ev_relay_mes.feature.production.api.ProductionLogResponseDto;
import com.human.ev_relay_mes.feature.production.api.ProductionLogSearchRequestDto;
import com.human.ev_relay_mes.feature.production.api.ProductionOperations;
import com.human.ev_relay_mes.feature.production.api.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import com.human.ev_relay_mes.feature.production.internal.repository.ProductionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionService implements ProductionOperations {

    private final ProductionLogRepository productionLogRepository;
    private final MachineRegistry machineRegistry;
    private final MasterDataLookup masterDataLookup;
    private final LotRepository lotRepository;
    private final ProductionInputQuantityResolver inputQuantityResolver;
    private final ProductionResultValidator resultValidator;
    private final ProductionProcessCompletionCoordinator completionCoordinator;
    private final ProductionLogResponseAssembler responseAssembler;

    @Transactional
    public ProductionLogResponseDto saveResult(ProductionResultReceiveRequestDto request) {
        String eventId = RequestValues.trimToNull(request.getEventId());
        resultValidator.validateRequest(request);
        if (ProcessCodes.FINAL_INSPECTION.equals(request.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_STATUS,
                    "OP70 실적은 검사 측정값을 집계해 Backend가 생성합니다.");
        }

        Lot lot = lotRepository.findByLotNoForUpdate(request.getLotNo())
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
        if (eventId != null) {
            Optional<ProductionLog> existing = productionLogRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return responseAssembler.toResponse(existing.get());
            }
        }

        List<ProductionLog> processLogs = productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        request.getLotNo(), request.getProcessCode());
        ProductionLog currentLog = processLogs.stream().findFirst().orElse(null);
        if (currentLog != null && request.getInputQty() <= currentLog.getInputQty()) {
            return responseAssembler.toResponse(currentLog);
        }

        resultValidator.validateLot(lot, request.getProcessCode());
        Machine machine = machineRegistry.getRequiredMachine(request.getMachineId());
        Process process = masterDataLookup.getRequiredProcess(request.getProcessCode());
        resultValidator.validateMachineProcess(machine, process);

        int expectedInputQty = inputQuantityResolver.resolveForResult(lot, process);
        resultValidator.validateCumulativeQuantity(
                expectedInputQty, request.getStatus(), request.getInputQty());
        String logStatus = request.getInputQty() == expectedInputQty
                ? "COMPLETED" : "RUNNING";

        if (currentLog == null) {
            currentLog = ProductionLog.builder()
                    .eventId(eventId)
                    .lot(lot)
                    .machine(machine)
                    .process(process)
                    .startedAt(request.getStartedAt())
                    .build();
        }
        updateLog(currentLog, request, logStatus);
        ProductionLog savedLog = productionLogRepository.save(currentLog);

        if (request.getInputQty() == expectedInputQty) {
            completionCoordinator.complete(
                    lot, machine, process, request.getOkQty(), currentLog.getEndedAt());
        }
        return responseAssembler.toResponse(savedLog);
    }

    @Transactional
    public ProductionLogResponseDto completeEvaluatedProcess(
            Lot lot, Machine machine, Process process,
            int inputQty, int okQty, int ngQty) {
        if (inputQty != okQty + ngQty) {
            throw new CustomException(ErrorCode.PRODUCTION_QUANTITY_MISMATCH);
        }
        String eventId = "QUALITY-AGG-" + lot.getLotNo() + "-" + process.getProcessCode();
        Optional<ProductionLog> existing = productionLogRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            return responseAssembler.toResponse(existing.get());
        }
        if (!productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), process.getProcessCode())
                .isEmpty()) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE,
                    "해당 공정의 생산 실적이 이미 존재합니다.");
        }

        ProductionLog log = ProductionLog.builder()
                .eventId(eventId)
                .lot(lot)
                .machine(machine)
                .process(process)
                .inputQty(inputQty)
                .okQty(okQty)
                .ngQty(ngQty)
                .status("COMPLETED")
                .endedAt(LocalDateTime.now())
                .build();
        ProductionLog saved = productionLogRepository.save(log);
        completionCoordinator.complete(lot, machine, process, okQty, saved.getEndedAt());
        return responseAssembler.toResponse(saved);
    }

    public int expectedInputQtyFor(Lot lot, Process process) {
        return inputQuantityResolver.resolveForResult(lot, process);
    }

    public List<ProductionLogResponseDto> search(ProductionLogSearchRequestDto condition) {
        RequestValues.validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return productionLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(log -> condition.getWorkOrderId() == null
                        || log.getLot().getWorkOrder().getWorkOrderId()
                        .equals(condition.getWorkOrderId()))
                .filter(log -> RequestValues.isBlank(condition.getLotNo())
                        || log.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(log -> RequestValues.isBlank(condition.getMachineId())
                        || log.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(log -> RequestValues.isBlank(condition.getProcessCode())
                        || log.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(log -> RequestValues.isBlank(condition.getStatus())
                        || log.getStatus().equalsIgnoreCase(condition.getStatus()))
                .filter(log -> RequestValues.isWithinInclusive(
                        log.getCreatedAt(), condition.getStartAt(), condition.getEndAt()))
                .map(responseAssembler::toResponse)
                .toList();
    }

    public ProductionLogResponseDto getProductionLog(Long id) {
        return productionLogRepository.findById(id)
                .map(responseAssembler::toResponse)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCTION_LOG_NOT_FOUND));
    }

    private void updateLog(
            ProductionLog log,
            ProductionResultReceiveRequestDto request,
            String status) {
        log.setInputQty(request.getInputQty());
        log.setOkQty(request.getOkQty());
        log.setNgQty(request.getNgQty());
        log.setStatus(status);
        if (log.getStartedAt() == null) {
            log.setStartedAt(request.getStartedAt());
        }
        log.setEndedAt("COMPLETED".equals(status)
                ? (request.getEndedAt() == null ? LocalDateTime.now() : request.getEndedAt())
                : null);
    }
}
