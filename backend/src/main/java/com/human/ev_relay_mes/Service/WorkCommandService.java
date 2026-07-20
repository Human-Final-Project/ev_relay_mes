package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.Dto.Response.WorkCommandResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.WorkCommand;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.InspectionUnitResultRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import com.human.ev_relay_mes.Repository.WorkCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkCommandService {

    private static final String PARALLEL_PROCESS_1 = "OP20";
    private static final String PARALLEL_PROCESS_2 = "OP30";
    private static final long DISPATCH_ACK_TIMEOUT_SECONDS = 10L;

    private static final EnumSet<WorkCommand.Status> ACTIVE_STATUSES = EnumSet.of(
            WorkCommand.Status.PENDING,
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);
    private static final EnumSet<WorkCommand.Status> MACHINE_RESERVED_STATUSES = EnumSet.of(
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);
    private static final EnumSet<WorkCommand.Status> INTERRUPTIBLE_STATUSES = EnumSet.of(
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);

    private final WorkCommandRepository workCommandRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final ProductionLogRepository productionLogRepository;
    private final InspectionUnitResultRepository inspectionUnitResultRepository;
    private final LotProcessResponsibleService lotProcessResponsibleService;
    private final InspectionStandardService inspectionStandardService;

    @Transactional
    public List<WorkCommandResponseDto> createInitialStartCommands(Lot lot) {
        Process op20 = activeProcess(PARALLEL_PROCESS_1);
        Process op30 = activeProcess(PARALLEL_PROCESS_2);
        if (lot.getCurrentProcess() != null
                && PARALLEL_PROCESS_1.equals(lot.getCurrentProcess().getProcessCode())
                && op20 != null && op30 != null) {
            return List.of(
                    createStartCommand(lot, op20, lot.getInputQty()),
                    createStartCommand(lot, op30, lot.getInputQty()));
        }
        if (lot.getCurrentProcess() == null) {
            throw new CustomException(ErrorCode.PROCESS_NOT_FOUND);
        }
        return List.of(createStartCommand(lot, lot.getCurrentProcess(), lot.getInputQty()));
    }

    @Transactional
    public WorkCommandResponseDto createStartCommand(Lot lot, Process process, int inputQty) {
        if (inputQty <= 0) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_QUANTITY,
                    "작업명령 투입 수량은 1 이상이어야 합니다.");
        }
        if (workCommandRepository
                .existsByLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusIn(
                        lot.getLotNo(), process.getProcessCode(), WorkCommand.CommandType.START, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.WORK_COMMAND_ALREADY_EXISTS);
        }

        Machine machine = selectMachine(process.getProcessCode());
        WorkCommand command = WorkCommand.builder()
                .commandType(WorkCommand.CommandType.START)
                .machine(machine)
                .process(process)
                .lot(lot)
                .inputQty(inputQty)
                .build();
        return WorkCommandResponseDto.fromEntity(workCommandRepository.save(command));
    }

    @Transactional
    public List<WorkCommandResponseDto> claimPendingCommands() {
        return claimPendingCommands(null);
    }

    @Transactional
    public List<WorkCommandResponseDto> claimPendingCommands(String machineId) {
        String normalizedMachineId = machineId == null ? null : machineId.trim();
        LocalDateTime now = LocalDateTime.now();
        requeueStaleDispatched(normalizedMachineId, now.minusSeconds(DISPATCH_ACK_TIMEOUT_SECONDS));
        List<WorkCommand> pending = normalizedMachineId == null || normalizedMachineId.isBlank()
                ? workCommandRepository.findByStatusForUpdate(WorkCommand.Status.PENDING)
                : workCommandRepository.findByMachineAndStatusForDispatch(
                        normalizedMachineId, WorkCommand.Status.PENDING);

        return pending.stream()
                .filter(this::canDispatch)
                .filter(command -> !workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                        command.getMachine().getMachineId(), MACHINE_RESERVED_STATUSES))
                .map(command -> {
                    command.setStatus(WorkCommand.Status.DISPATCHED);
                    command.setDispatchedAt(now);
                    return WorkCommandResponseDto.fromEntity(command);
                })
                .toList();
    }

    private void requeueStaleDispatched(String machineId, LocalDateTime cutoff) {
        List<WorkCommand> stale = machineId == null || machineId.isBlank()
                ? workCommandRepository.findStaleDispatchedForUpdate(
                        WorkCommand.Status.DISPATCHED, cutoff)
                : workCommandRepository.findStaleDispatchedByMachineForUpdate(
                        machineId, WorkCommand.Status.DISPATCHED, cutoff);
        stale.forEach(command -> {
            command.setStatus(WorkCommand.Status.PENDING);
            command.setDispatchedAt(null);
        });
    }

    @Transactional
    public void pauseForMachineError(String machineId) {
        LocalDateTime now = LocalDateTime.now();
        workCommandRepository.findByMachineAndStatusInForUpdate(machineId, INTERRUPTIBLE_STATUSES)
                .forEach(command -> {
                    command.setStatus(WorkCommand.Status.CANCELED);
                    command.setCompletedAt(now);
                    Lot lot = command.getLot();
                    if (lot.getStatus() == Lot.Status.RUNNING) {
                        lot.setStatus(Lot.Status.HOLD);
                    }
                });
    }

    @Transactional
    public Optional<WorkCommandResponseDto> createResumeCommand(String machineId) {
        WorkCommand interrupted = workCommandRepository
                .findFirstByMachine_MachineIdAndStatusOrderByCreatedAtDescCommandIdDesc(
                        machineId, WorkCommand.Status.CANCELED)
                .orElse(null);
        if (interrupted == null || interrupted.getLot().getStatus() != Lot.Status.HOLD) {
            return Optional.empty();
        }

        Lot lot = interrupted.getLot();
        Process process = interrupted.getProcess();
        Machine machine = interrupted.getMachine();
        if (workCommandRepository
                .existsByLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusIn(
                        lot.getLotNo(), process.getProcessCode(),
                        WorkCommand.CommandType.RESUME, ACTIVE_STATUSES)) {
            throw new CustomException(ErrorCode.WORK_COMMAND_ALREADY_EXISTS);
        }

        int targetQty = originalTargetQty(interrupted);
        int processedQty;
        if (InspectionStandardService.INSPECTION_PROCESS_CODE.equals(process.getProcessCode())) {
            processedQty = Math.toIntExact(inspectionUnitResultRepository
                    .countByLot_LotNoAndProcess_ProcessCode(
                            lot.getLotNo(), process.getProcessCode()));
        } else {
            processedQty = productionLogRepository
                    .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                            lot.getLotNo(), process.getProcessCode())
                    .stream().mapToInt(log -> log.getInputQty()).sum();
        }
        int remainingQty = targetQty - processedQty;
        if (remainingQty <= 0) {
            return Optional.empty();
        }

        WorkCommand resume = WorkCommand.builder()
                .commandType(WorkCommand.CommandType.RESUME)
                .machine(machine)
                .process(process)
                .lot(lot)
                .inputQty(remainingQty)
                .build();
        return Optional.of(WorkCommandResponseDto.fromEntity(workCommandRepository.save(resume)));
    }

    @Transactional
    public boolean completeResumeCommand(Lot lot, Process process, Machine machine) {
        List<WorkCommand> commands = workCommandRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndMachine_MachineIdAndCommandTypeAndStatusIn(
                        lot.getLotNo(), process.getProcessCode(), machine.getMachineId(),
                        WorkCommand.CommandType.RESUME,
                        EnumSet.of(WorkCommand.Status.DISPATCHED, WorkCommand.Status.ACCEPTED));
        if (commands.isEmpty()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        commands.forEach(command -> {
            command.setStatus(WorkCommand.Status.COMPLETED);
            command.setCompletedAt(now);
        });
        return true;
    }

    @Transactional
    public WorkCommandResponseDto releaseDispatchedCommand(Long commandId, String machineId) {
        WorkCommand command = workCommandRepository.findByIdForUpdate(commandId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_COMMAND_NOT_FOUND));
        if (machineId == null || machineId.isBlank()
                || !command.getMachine().getMachineId().equals(machineId.trim())) {
            throw new CustomException(ErrorCode.WORK_COMMAND_MACHINE_MISMATCH);
        }
        if (command.getStatus() == WorkCommand.Status.PENDING) {
            return WorkCommandResponseDto.fromEntity(command);
        }
        if (command.getStatus() != WorkCommand.Status.DISPATCHED) {
            throw new CustomException(ErrorCode.INVALID_WORK_COMMAND_STATUS,
                    "L1 전송 전에 DISPATCHED 상태인 명령만 반환할 수 있습니다.");
        }
        command.setStatus(WorkCommand.Status.PENDING);
        command.setDispatchedAt(null);
        return WorkCommandResponseDto.fromEntity(command);
    }

    @Transactional
    public WorkCommandResponseDto acknowledge(WorkCommandAckRequestDto dto) {
        WorkCommand command = workCommandRepository.findByIdForUpdate(dto.getCommandId())
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_COMMAND_NOT_FOUND));
        if (!command.getMachine().getMachineId().equals(dto.getMachineId())) {
            throw new CustomException(ErrorCode.WORK_COMMAND_MACHINE_MISMATCH);
        }
        WorkCommand.Status acknowledgedStatus =
                WorkCommand.Status.valueOf(dto.getAckStatus().toUpperCase());
        boolean acceptedAlreadyProcessed =
                acknowledgedStatus == WorkCommand.Status.ACCEPTED
                        && command.getAcknowledgedAt() != null
                        && EnumSet.of(
                                WorkCommand.Status.ACCEPTED,
                                WorkCommand.Status.COMPLETED,
                                WorkCommand.Status.CANCELED)
                        .contains(command.getStatus());
        if (command.getStatus() == acknowledgedStatus || acceptedAlreadyProcessed) {
            if (acknowledgedStatus == WorkCommand.Status.ACCEPTED) {
                captureStartContext(command);
            }
            return WorkCommandResponseDto.fromEntity(command);
        }
        if (command.getStatus() != WorkCommand.Status.DISPATCHED) {
            throw new CustomException(ErrorCode.INVALID_WORK_COMMAND_STATUS);
        }

        command.setStatus(acknowledgedStatus);
        if (acknowledgedStatus == WorkCommand.Status.ACCEPTED) {
            captureStartContext(command);
        }
        command.setAckMessage(dto.getMessage());
        command.setAcknowledgedAt(LocalDateTime.now());
        return WorkCommandResponseDto.fromEntity(command);
    }


    private void captureStartContext(WorkCommand command) {
        if (command.getCommandType() == WorkCommand.CommandType.STOP) {
            return;
        }
        lotProcessResponsibleService.captureIfAbsent(
                command.getLot(), command.getProcess(), command.getMachine());
        if (InspectionStandardService.INSPECTION_PROCESS_CODE.equals(
                command.getProcess().getProcessCode())) {
            inspectionStandardService.captureStandardsIfAbsent(
                    command.getLot(), command.getProcess());
        }
    }

    @Transactional
    public void completeStartCommand(Lot lot, Process process, Machine machine) {
        List<WorkCommand> commands = workCommandRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndMachine_MachineIdAndCommandTypeAndStatusIn(
                        lot.getLotNo(), process.getProcessCode(), machine.getMachineId(),
                        WorkCommand.CommandType.START,
                        EnumSet.of(WorkCommand.Status.DISPATCHED, WorkCommand.Status.ACCEPTED));
        LocalDateTime now = LocalDateTime.now();
        commands.forEach(command -> {
            command.setStatus(WorkCommand.Status.COMPLETED);
            command.setCompletedAt(now);
        });
    }

    public List<WorkCommandResponseDto> getCommands(String lotNo) {
        return workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc(lotNo)
                .stream().map(WorkCommandResponseDto::fromEntity).toList();
    }

    private boolean canDispatch(WorkCommand command) {
        Machine.Status machineStatus = command.getMachine().getStatus();
        if (command.getCommandType() == WorkCommand.CommandType.RESUME) {
            return machineStatus == Machine.Status.ERROR || machineStatus == Machine.Status.STOPPED;
        }
        return machineStatus == Machine.Status.IDLE;
    }

    private int originalTargetQty(WorkCommand interrupted) {
        return workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc(interrupted.getLot().getLotNo())
                .stream()
                .filter(command -> command.getCommandType() == WorkCommand.CommandType.START)
                .filter(command -> command.getProcess().getProcessCode()
                        .equals(interrupted.getProcess().getProcessCode()))
                .filter(command -> command.getMachine().getMachineId()
                        .equals(interrupted.getMachine().getMachineId()))
                .map(WorkCommand::getInputQty)
                .findFirst()
                .orElse(interrupted.getInputQty());
    }

    private Process activeProcess(String processCode) {
        return processRepository.findById(processCode)
                                .orElse(null);
    }

    private Machine selectMachine(String processCode) {
        List<Machine> machines = machineRepository.findUsableByProcessForUpdate(processCode);
        return machines.stream()
                .min(Comparator
                        .comparingLong((Machine machine) -> workCommandRepository
                                .countByMachine_MachineIdAndStatusIn(machine.getMachineId(), ACTIVE_STATUSES))
                        .thenComparing(machine -> machine.getStatus() == Machine.Status.IDLE ? 0 : 1)
                        .thenComparing(Machine::getMachineId))
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_MACHINE_NOT_CONFIGURED));
    }
}
