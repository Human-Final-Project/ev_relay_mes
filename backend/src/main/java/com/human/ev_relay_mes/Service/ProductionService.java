package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.ProductionLogSearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProductionLogResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
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
public class ProductionService {

    private static final String PARALLEL_PROCESS_1 = "OP20";
    private static final String PARALLEL_PROCESS_2 = "OP30";
    private static final String ASSEMBLY_PROCESS = "OP40_OP50";
    private static final String INSPECTION_PROCESS = "OP70";

    private final ProductionLogRepository productionLogRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final LotRepository lotRepository;
    private final WorkCommandService workCommandService;

    @Transactional
    public ProductionLogResponseDto saveResult(ProductionResultReceiveRequestDto dto) {
        String eventId = normalizeEventId(dto.getEventId());
        if (eventId != null) {
            Optional<ProductionLog> existing = productionLogRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        validateRequest(dto);
        if (INSPECTION_PROCESS.equals(dto.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_STATUS,
                    "OP70 실적은 검사 측정값을 집계해 Backend가 생성합니다.");
        }
        Lot lot = lotRepository.findByLotNoForUpdate(dto.getLotNo())
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
        validateLot(lot, dto.getProcessCode());

        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        validateMachineAndProcess(machine, process);

        List<ProductionLog> previousLogs = productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        dto.getLotNo(), dto.getProcessCode());
        int totalInputQty = sumInput(previousLogs) + dto.getInputQty();
        int totalOkQty = sumOk(previousLogs) + dto.getOkQty();
        int totalNgQty = sumNg(previousLogs) + dto.getNgQty();
        int expectedInputQty = expectedInputQty(lot, process);
        validateCumulativeQuantity(expectedInputQty, dto.getStatus(), totalInputQty);
        String logStatus = totalInputQty == expectedInputQty ? "COMPLETED" : "RUNNING";

        ProductionLog log = ProductionLog.builder()
                .eventId(eventId)
                .lot(lot)
                .machine(machine)
                .process(process)
                .inputQty(dto.getInputQty())
                .okQty(dto.getOkQty())
                .ngQty(dto.getNgQty())
                .status(logStatus)
                .startedAt(dto.getStartedAt())
                .endedAt(dto.getEndedAt())
                .build();
        ProductionLog savedLog = productionLogRepository.save(log);

        if (totalInputQty == expectedInputQty) {
            completeCurrentProcess(lot, machine, process, totalOkQty, totalNgQty, dto.getEndedAt());
        }
        return toResponse(savedLog);
    }


    @Transactional
    public ProductionLogResponseDto completeInspectionProcess(
            Lot lot, Machine machine, Process process,
            int inputQty, int okQty, int ngQty) {
        if (!INSPECTION_PROCESS.equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER,
                    "검사 집계 실적은 OP70에서만 생성할 수 있습니다.");
        }
        if (inputQty != okQty + ngQty) {
            throw new CustomException(ErrorCode.PRODUCTION_QUANTITY_MISMATCH);
        }
        String eventId = "INSPECTION-AGG-" + lot.getLotNo() + "-" + process.getProcessCode();
        Optional<ProductionLog> existing = productionLogRepository.findByEventId(eventId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        if (!productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), process.getProcessCode())
                .isEmpty()) {
            throw new CustomException(ErrorCode.DUPLICATE_RESOURCE,
                    "OP70 검사 집계 실적이 이미 존재합니다.");
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
        completeCurrentProcess(lot, machine, process, okQty, ngQty, saved.getEndedAt());
        return toResponse(saved);
    }

    public int expectedInputQtyFor(Lot lot, Process process) {
        return expectedInputQty(lot, process);
    }

    public List<ProductionLogResponseDto> search(ProductionLogSearchRequestDto condition) {
        validateSearchPeriod(condition);
        return productionLogRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .filter(log -> isBlank(condition.getLotNo())
                        || log.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(log -> isBlank(condition.getMachineId())
                        || log.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(log -> isBlank(condition.getProcessCode())
                        || log.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(log -> isBlank(condition.getStatus())
                        || log.getStatus().equalsIgnoreCase(condition.getStatus()))
                .filter(log -> isWithin(log.getCreatedAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    public ProductionLogResponseDto getProductionLog(Long id) {
        return productionLogRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCTION_LOG_NOT_FOUND));
    }

    private void validateRequest(ProductionResultReceiveRequestDto dto) {
        if (dto.getInputQty() != dto.getOkQty() + dto.getNgQty()) {
            throw new CustomException(ErrorCode.PRODUCTION_QUANTITY_MISMATCH);
        }
        if (dto.getStartedAt() != null && dto.getEndedAt() != null
                && dto.getStartedAt().isAfter(dto.getEndedAt())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "생산 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    private void validateLot(Lot lot, String processCode) {
        if (lot.getStatus() != Lot.Status.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS,
                    "생산 중인 LOT에만 실적을 등록할 수 있습니다.");
        }
        if (lot.getCurrentProcess() == null || !isProcessReady(lot, processCode)) {
            throw new CustomException(ErrorCode.INVALID_PROCESS_ORDER,
                    "LOT의 현재 공정과 생산실적 공정이 일치하지 않습니다.");
        }
    }

    private boolean isProcessReady(Lot lot, String processCode) {
        String currentProcessCode = lot.getCurrentProcess().getProcessCode();
        if (currentProcessCode.equals(processCode)) {
            return true;
        }
        return PARALLEL_PROCESS_1.equals(currentProcessCode)
                && PARALLEL_PROCESS_2.equals(processCode);
    }

    private void validateMachineAndProcess(Machine machine, Process process) {
        if (!"Y".equalsIgnoreCase(machine.getUseYn())) {
            throw new CustomException(ErrorCode.MACHINE_NOT_USABLE);
        }
        if (!"Y".equalsIgnoreCase(process.getUseYn())) {
            throw new CustomException(ErrorCode.PROCESS_NOT_USABLE);
        }
        if (!machine.getProcess().getProcessCode().equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "설비에 지정된 공정과 생산실적 공정이 일치하지 않습니다.");
        }
    }

    private void validateCumulativeQuantity(int expectedInputQty, String status, int totalInputQty) {
        if (totalInputQty > expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_QUANTITY,
                    "공정 생산수량이 LOT 투입수량을 초과합니다.");
        }
        if ("COMPLETED".equalsIgnoreCase(status) && totalInputQty < expectedInputQty) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_STATUS,
                    "LOT 투입수량을 모두 처리하기 전에는 공정을 완료할 수 없습니다.");
        }
    }

    private void completeCurrentProcess(
            Lot lot, Machine machine, Process process,
            int totalOkQty, int totalNgQty, LocalDateTime endedAt) {
        workCommandService.completeStartCommand(lot, process, machine);

        if (isParallelProcess(process.getProcessCode())) {
            advanceAfterParallelProcesses(lot);
            return;
        }

        Optional<Process> nextProcess = processRepository
                .findFirstByProcessOrderGreaterThanAndUseYnOrderByProcessOrderAsc(
                        process.getProcessOrder(), "Y");
        if (nextProcess.isPresent()) {
            lot.setCurrentProcess(nextProcess.get());
            if (totalOkQty > 0) {
                workCommandService.createStartCommand(lot, nextProcess.get(), totalOkQty);
            } else {
                lot.setStatus(Lot.Status.HOLD);
            }
            return;
        }

        lot.setOkQty(totalOkQty);
        lot.setNgQty(totalNgQty);
        lot.setStatus(Lot.Status.COMPLETED);
        lot.setCompletedAt(endedAt == null ? LocalDateTime.now() : endedAt);
        completeWorkOrderIfReady(lot);
    }

    private void advanceAfterParallelProcesses(Lot lot) {
        if (!isProcessCompleted(lot, PARALLEL_PROCESS_1)
                || !isProcessCompleted(lot, PARALLEL_PROCESS_2)) {
            return;
        }

        Process assembly = processRepository.findById(ASSEMBLY_PROCESS)
                .filter(process -> "Y".equalsIgnoreCase(process.getUseYn()))
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND,
                        "병렬 공정 합류 공정이 등록되어 있지 않습니다."));
        int assemblyInputQty = Math.min(
                processOkQty(lot, PARALLEL_PROCESS_1),
                processOkQty(lot, PARALLEL_PROCESS_2));
        lot.setCurrentProcess(assembly);
        if (assemblyInputQty > 0) {
            workCommandService.createStartCommand(lot, assembly, assemblyInputQty);
        } else {
            lot.setStatus(Lot.Status.HOLD);
        }
    }

    private int expectedInputQty(Lot lot, Process process) {
        if (isParallelProcess(process.getProcessCode())) {
            return lot.getInputQty();
        }
        if (ASSEMBLY_PROCESS.equals(process.getProcessCode())) {
            return Math.min(
                    processOkQty(lot, PARALLEL_PROCESS_1),
                    processOkQty(lot, PARALLEL_PROCESS_2));
        }
        return processRepository
                .findFirstByProcessOrderLessThanAndUseYnOrderByProcessOrderDesc(
                        process.getProcessOrder(), "Y")
                .map(previous -> processOkQty(lot, previous.getProcessCode()))
                .filter(quantity -> quantity > 0)
                .orElse(lot.getInputQty());
    }

    private boolean isProcessCompleted(Lot lot, String processCode) {
        List<ProductionLog> logs = productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), processCode);
        return sumInput(logs) == lot.getInputQty();
    }

    private int processOkQty(Lot lot, String processCode) {
        return sumOk(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), processCode));
    }

    private boolean isParallelProcess(String processCode) {
        return PARALLEL_PROCESS_1.equals(processCode) || PARALLEL_PROCESS_2.equals(processCode);
    }

    private void completeWorkOrderIfReady(Lot completedLot) {
        WorkOrder workOrder = completedLot.getWorkOrder();
        List<Lot> lots = lotRepository
                .findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(workOrder.getWorkOrderId());
        boolean allTerminal = lots.stream().allMatch(lot ->
                lot.getStatus() == Lot.Status.COMPLETED || lot.getStatus() == Lot.Status.SCRAPPED);
        int completedQty = lots.stream()
                .filter(lot -> lot.getStatus() == Lot.Status.COMPLETED)
                .mapToInt(Lot::getInputQty)
                .sum();
        if (allTerminal && completedQty >= workOrder.getTargetQty()) {
            workOrder.setStatus(WorkOrder.Status.COMPLETED);
        }
    }

    private int sumInput(List<ProductionLog> logs) {
        return logs.stream().mapToInt(ProductionLog::getInputQty).sum();
    }

    private int sumOk(List<ProductionLog> logs) {
        return logs.stream().mapToInt(ProductionLog::getOkQty).sum();
    }

    private int sumNg(List<ProductionLog> logs) {
        return logs.stream().mapToInt(ProductionLog::getNgQty).sum();
    }

    private void validateSearchPeriod(ProductionLogSearchRequestDto condition) {
        if (condition.getStartAt() != null && condition.getEndAt() != null
                && condition.getStartAt().isAfter(condition.getEndAt())) {
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
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    private ProductionLogResponseDto toResponse(ProductionLog log) {
        return ProductionLogResponseDto.builder()
                .productionLogId(log.getProductionLogId())
                .lotNo(log.getLot().getLotNo())
                .machineId(log.getMachine().getMachineId())
                .machineName(log.getMachine().getMachineName())
                .processCode(log.getProcess().getProcessCode())
                .processName(log.getProcess().getProcessName())
                .inputQty(log.getInputQty())
                .okQty(log.getOkQty())
                .ngQty(log.getNgQty())
                .status(log.getStatus())
                .startedAt(log.getStartedAt())
                .endedAt(log.getEndedAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
