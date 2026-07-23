package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;
import com.human.ev_relay_mes.Entity.WorkCommand;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import com.human.ev_relay_mes.Repository.InspectionUnitResultRepository;
import com.human.ev_relay_mes.Repository.WorkCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.EnumSet;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineService {

    private static final EnumSet<WorkCommand.Status> ACTIVE_COMMAND_STATUSES = EnumSet.of(
            WorkCommand.Status.DISPATCHED, WorkCommand.Status.ACCEPTED);

    private final MachineRepository machineRepository;
    private final MachineStatusHistoryRepository machineStatusHistoryRepository;
    private final ProcessRepository processRepository;
    private final LotRepository lotRepository;
    private final WorkCommandService workCommandService;
    private final WorkCommandRepository workCommandRepository;
    private final ProductionLogRepository productionLogRepository;
    private final InspectionUnitResultRepository inspectionUnitResultRepository;
    private final ProductionScheduleRequestService productionScheduleRequestService;

    // 설비 현황 화면에 전체 설비의 기본 정보와 현재 상태를 표시할 때 사용한다.
    public List<MachineResponseDto> getMachines() {
        return machineRepository.findAll().stream().map(this::toMachineResponse).toList();
    }

    // 설비 상세 화면에서 특정 설비의 정보와 현재 상태를 조회할 때 사용한다.
    public MachineResponseDto getMachine(String machineId) {
        return toMachineResponse(findMachine(machineId));
    }

    // L2가 전달한 설비 상태를 현재 상태에 반영하고 상태 변경 이력을 남길 때 사용한다.
    @Transactional
    public MachineStatusHistoryResponseDto updateStatus(MachineStatusReceiveRequestDto dto) {
        String eventId = normalizeEventId(dto.getEventId());
        if (eventId != null) {
            var existing = machineStatusHistoryRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return toHistoryResponse(existing.get());
            }
        }
        Machine machine = findMachine(dto.getMachineId());
        Machine.Status previousStatus = machine.getStatus();
        Machine.Status status = parseStatus(dto.getStatus());
        Lot lot = isBlank(dto.getLotNo()) ? null : findLot(dto.getLotNo());
        com.human.ev_relay_mes.Entity.Process process = isBlank(dto.getProcessCode()) ? null
                : processRepository.findById(dto.getProcessCode())
                        .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));

        boolean resumeCompleted = status == Machine.Status.RUNNING
                && lot != null
                && process != null
                && lot.getStatus() == Lot.Status.HOLD
                && workCommandService.completeResumeCommand(lot, process, machine);
        if (resumeCompleted) {
            lot.setStatus(Lot.Status.RUNNING);
        }

        var latestHistory = machineStatusHistoryRepository
                .findFirstByMachine_MachineIdOrderByRecordedAtDesc(machine.getMachineId());
        if (!resumeCompleted
                && previousStatus == status
                && latestHistory.filter(history -> sameStatusSnapshot(history, status, lot, process)).isPresent()) {
            if (status == Machine.Status.IDLE) {
                productionScheduleRequestService.requestMachine(machine.getMachineId());
            }
            return toHistoryResponse(latestHistory.get());
        }

        machine.setStatus(status);
        MachineStatusHistory history = MachineStatusHistory.builder()
                .eventId(eventId)
                .machine(machine)
                .status(status)
                .lot(lot)
                .process(process)
                .message(dto.getMessage())
                .build();
        MachineStatusHistory savedHistory = machineStatusHistoryRepository.save(history);
        if (status == Machine.Status.IDLE) {
            productionScheduleRequestService.requestMachine(machine.getMachineId());
        }
        return toHistoryResponse(savedHistory);
    }

    // 설비 상세 화면에서 특정 설비의 상태 변화 이력을 최신순으로 표시할 때 사용한다.
    public List<MachineStatusHistoryResponseDto> getStatusHistory(String machineId) {
        findMachine(machineId);
        return machineStatusHistoryRepository.findByMachine_MachineIdOrderByRecordedAtDesc(machineId)
                .stream().map(this::toHistoryResponse).toList();
    }

    private boolean sameStatusSnapshot(
            MachineStatusHistory history,
            Machine.Status status,
            Lot lot,
            com.human.ev_relay_mes.Entity.Process process) {
        String historyLotNo = history.getLot() == null ? null : history.getLot().getLotNo();
        String requestedLotNo = lot == null ? null : lot.getLotNo();
        String historyProcessCode = history.getProcess() == null
                ? null : history.getProcess().getProcessCode();
        String requestedProcessCode = process == null ? null : process.getProcessCode();
        return history.getStatus() == status
                && java.util.Objects.equals(historyLotNo, requestedLotNo)
                && java.util.Objects.equals(historyProcessCode, requestedProcessCode);
    }

    // 요청된 설비가 실제 등록된 설비인지 확인하고 Entity를 가져올 때 내부적으로 사용한다.
    private Machine findMachine(String machineId) {
        return machineRepository.findById(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
    }

    // 설비 상태 메시지에 포함된 LOT 번호를 생산 LOT와 연결할 때 내부적으로 사용한다.
    private Lot findLot(String lotNo) {
        return lotRepository.findByLotNo(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    // 외부에서 받은 상태 문자열을 설비 상태 Enum으로 안전하게 변환할 때 사용한다.
    private Machine.Status parseStatus(String status) {
        try {
            return Machine.Status.valueOf(status.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_MACHINE_STATUS);
        }
    }

    // LOT나 공정처럼 생략 가능한 메시지 값이 비어 있는지 판단할 때 사용한다.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEventId(String eventId) {
        return isBlank(eventId) ? null : eventId.trim();
    }

    // 설비 Entity를 설비 현황 화면과 API에 전달할 응답 DTO로 변환할 때 사용한다.
    private MachineResponseDto toMachineResponse(Machine machine) {
        MachineResponseDto.MachineResponseDtoBuilder builder = MachineResponseDto.builder()
                .machineId(machine.getMachineId())
                .machineName(machine.getMachineName())
                .machineType(machine.getMachineType())
                .processCode(machine.getProcess().getProcessCode())
                .processName(machine.getProcess().getProcessName())
                .status(machine.getStatus().name())
                .createdAt(machine.getCreatedAt())
                .updatedAt(machine.getUpdatedAt());
        if (machine.getStatus() == Machine.Status.RUNNING) {
            workCommandRepository
                    .findFirstByMachine_MachineIdAndStatusInOrderByCreatedAtDescCommandIdDesc(
                            machine.getMachineId(), ACTIVE_COMMAND_STATUSES)
                    .ifPresent(command -> appendProgress(builder, command));
        }
        return builder.build();
    }

    private void appendProgress(
            MachineResponseDto.MachineResponseDtoBuilder builder,
            WorkCommand command) {
        String lotNo = command.getLot().getLotNo();
        String processCode = command.getProcess().getProcessCode();
        int evaluatedQty = Math.toIntExact(inspectionUnitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
                        lotNo, processCode,
                        com.human.ev_relay_mes.Entity.InspectionUnitResult.EvaluationStatus.COMPLETED));
        int productionQty = productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(lotNo, processCode)
                .stream().mapToInt(log -> log.getInputQty()).sum();
        int processedQty = Math.max(evaluatedQty, productionQty);
        int targetQty = command.getCommandType() == WorkCommand.CommandType.RESUME
                ? workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc(lotNo).stream()
                        .filter(item -> item.getCommandType() == WorkCommand.CommandType.START)
                        .filter(item -> item.getProcess().getProcessCode().equals(processCode))
                        .mapToInt(WorkCommand::getInputQty)
                        .findFirst()
                        .orElse(processedQty + command.getInputQty())
                : command.getInputQty();
        int progressPercent = targetQty == 0
                ? 0 : Math.min(100, processedQty * 100 / targetQty);
        builder.currentLotNo(lotNo)
                .targetQty(targetQty)
                .processedQty(processedQty)
                .progressPercent(progressPercent);
    }

    // 설비 상태 이력 Entity를 상태 이력 화면에 전달할 응답 DTO로 변환할 때 사용한다.
    private MachineStatusHistoryResponseDto toHistoryResponse(MachineStatusHistory history) {
        return MachineStatusHistoryResponseDto.builder()
                .machineStatusHistoryId(history.getMachineStatusHistoryId())
                .machineId(history.getMachine().getMachineId())
                .machineName(history.getMachine().getMachineName())
                .status(history.getStatus().name())
                .lotNo(history.getLot() == null ? null : history.getLot().getLotNo())
                .processCode(history.getProcess() == null ? null : history.getProcess().getProcessCode())
                .processName(history.getProcess() == null ? null : history.getProcess().getProcessName())
                .recordedAt(history.getRecordedAt())
                .message(history.getMessage())
                .build();
    }
}
