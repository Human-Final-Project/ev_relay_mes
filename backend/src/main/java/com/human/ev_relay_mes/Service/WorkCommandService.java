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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
    private static final EnumSet<WorkCommand.Status> STARTED_PROCESS_STATUSES = EnumSet.of(
            WorkCommand.Status.PENDING,
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED,
            WorkCommand.Status.COMPLETED,
            WorkCommand.Status.CANCELED);
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

    /**
     * 기존 호출부와 테스트를 위한 엄격한 생성 API다.
     * 파이프라인 스케줄러는 설비가 바쁠 때 예외를 발생시키지 않는 try 메서드를 사용한다.
     */
    @Transactional
    public List<WorkCommandResponseDto> createInitialStartCommands(Lot lot) {
        return tryCreateInitialStartCommands(lot)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.MACHINE_NOT_IDLE,
                        "OP20·OP30 설비가 모두 대기 상태일 때 초기 공정을 시작할 수 있습니다."));
    }

    /**
     * OP20과 OP30 설비를 같은 트랜잭션에서 잠그고 두 START 명령을 함께 예약한다.
     * 두 설비 중 하나라도 바쁘면 아무 명령도 생성하지 않는다.
     */
    @Transactional
    public Optional<List<WorkCommandResponseDto>> tryCreateInitialStartCommands(Lot lot) {
        Process op20 = activeProcess(PARALLEL_PROCESS_1);
        Process op30 = activeProcess(PARALLEL_PROCESS_2);
        if (op20 == null || op30 == null) {
            throw new CustomException(ErrorCode.PROCESS_NOT_FOUND);
        }
        if (lot.getCurrentProcess() == null
                || !PARALLEL_PROCESS_1.equals(lot.getCurrentProcess().getProcessCode())) {
            return Optional.empty();
        }
        if (hasActiveExecution(lot.getLotNo(), PARALLEL_PROCESS_1)
                || hasActiveExecution(lot.getLotNo(), PARALLEL_PROCESS_2)) {
            return Optional.empty();
        }

        // 공정 코드 순서가 항상 같아 동시 스케줄링 시 설비 잠금 순서도 고정된다.
        Machine wind = findAvailableMachineForUpdate(PARALLEL_PROCESS_1).orElse(null);
        Machine weld = findAvailableMachineForUpdate(PARALLEL_PROCESS_2).orElse(null);
        if (wind == null || weld == null) {
            return Optional.empty();
        }

        WorkCommand windCommand = buildStartCommand(lot, op20, wind, lot.getInputQty());
        WorkCommand weldCommand = buildStartCommand(lot, op30, weld, lot.getInputQty());
        return Optional.of(List.of(
                WorkCommandResponseDto.fromEntity(workCommandRepository.save(windCommand)),
                WorkCommandResponseDto.fromEntity(workCommandRepository.save(weldCommand))));
    }

    @Transactional
    public WorkCommandResponseDto createStartCommand(Lot lot, Process process, int inputQty) {
        return tryCreateStartCommand(lot, process, inputQty)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.MACHINE_NOT_IDLE,
                        "해당 공정 설비가 사용 중이거나 이미 예약되어 있습니다."));
    }

    /**
     * 설비 행을 잠근 뒤 IDLE 상태와 활성 명령 부재를 확인하여 START를 예약한다.
     * PENDING 명령 자체가 RESERVED 역할을 하므로 별도 설비 상태를 추가하지 않는다.
     */
    @Transactional
    public Optional<WorkCommandResponseDto> tryCreateStartCommand(
            Lot lot, Process process, int inputQty) {
        if (inputQty <= 0) {
            throw new CustomException(ErrorCode.INVALID_PRODUCTION_QUANTITY,
                    "작업명령 투입 수량은 1 이상이어야 합니다.");
        }
        if (hasActiveExecution(lot.getLotNo(), process.getProcessCode())) {
            return Optional.empty();
        }
        Machine machine = findAvailableMachineForUpdate(process.getProcessCode()).orElse(null);
        if (machine == null) {
            return Optional.empty();
        }
        WorkCommand command = buildStartCommand(lot, process, machine, inputQty);
        return Optional.of(WorkCommandResponseDto.fromEntity(workCommandRepository.save(command)));
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

        List<WorkCommandResponseDto> claimed = new ArrayList<>();
        Set<String> reservedDuringClaim = new HashSet<>();
        for (WorkCommand command : pending) {
            String commandMachineId = command.getMachine().getMachineId();
            if (!canDispatch(command)
                    || reservedDuringClaim.contains(commandMachineId)
                    || workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                            commandMachineId, MACHINE_RESERVED_STATUSES)) {
                continue;
            }
            command.setStatus(WorkCommand.Status.DISPATCHED);
            command.setDispatchedAt(now);
            reservedDuringClaim.add(commandMachineId);
            claimed.add(WorkCommandResponseDto.fromEntity(command));
        }
        return claimed;
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
        return createResumeCommand(machineId, null, null);
    }

    /**
     * 알람 이력의 LOT·공정 컨텍스트를 우선 사용해 정확히 중단된 명령만 재개한다.
     * 동일 RESUME이 이미 PENDING/DISPATCHED/ACCEPTED이면 새 명령을 만들지 않고
     * 기존 명령을 반환하므로 알람 해제 중복 요청에도 멱등하게 동작한다.
     */
    @Transactional
    public Optional<WorkCommandResponseDto> createResumeCommand(
            String machineId, String lotNo, String processCode) {
        Machine lockedMachine = machineRepository.findByIdForUpdate(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));

        WorkCommand interrupted = findInterruptedCommand(machineId, lotNo, processCode);
        if (interrupted == null || interrupted.getLot().getStatus() != Lot.Status.HOLD) {
            return Optional.empty();
        }

        Lot lot = interrupted.getLot();
        Process process = interrupted.getProcess();
        Machine machine = interrupted.getMachine();
        Optional<WorkCommand> existingResume = workCommandRepository
                .findFirstByMachine_MachineIdAndLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusInOrderByCreatedAtDescCommandIdDesc(
                        machineId, lot.getLotNo(), process.getProcessCode(),
                        WorkCommand.CommandType.RESUME, ACTIVE_STATUSES);
        if (existingResume.isPresent()) {
            return Optional.of(WorkCommandResponseDto.fromEntity(existingResume.get()));
        }
        if (workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                machineId, ACTIVE_STATUSES)) {
            return Optional.empty();
        }

        int targetQty = originalTargetQty(interrupted);
        int evaluatedQty = Math.toIntExact(inspectionUnitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
                        lot.getLotNo(), process.getProcessCode(),
                        com.human.ev_relay_mes.Entity.InspectionUnitResult.EvaluationStatus.COMPLETED));
        int productionQty = productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), process.getProcessCode())
                .stream().mapToInt(log -> log.getInputQty()).sum();
        int processedQty = Math.max(evaluatedQty, productionQty);
        int remainingQty = targetQty - processedQty;
        if (remainingQty <= 0) {
            return Optional.empty();
        }

        // L1은 ERROR_PAUSED/STOPPED 상태에서만 RESUME을 받는다. Backend 상태가
        // 지연된 IDLE/RUNNING 스냅샷으로 덮였더라도 HOLD LOT이 있으면 ERROR로 정렬한다.
        if (lockedMachine.getStatus() != Machine.Status.ERROR
                && lockedMachine.getStatus() != Machine.Status.STOPPED) {
            lockedMachine.setStatus(Machine.Status.ERROR);
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

    public boolean hasHeldInterruptedWork(String machineId, String lotNo, String processCode) {
        WorkCommand interrupted = findInterruptedCommand(machineId, lotNo, processCode);
        return interrupted != null && interrupted.getLot().getStatus() == Lot.Status.HOLD;
    }

    private WorkCommand findInterruptedCommand(
            String machineId, String lotNo, String processCode) {
        if (lotNo != null && !lotNo.isBlank()
                && processCode != null && !processCode.isBlank()) {
            return workCommandRepository
                    .findFirstByMachine_MachineIdAndLot_LotNoAndProcess_ProcessCodeAndStatusOrderByCreatedAtDescCommandIdDesc(
                            machineId, lotNo, processCode, WorkCommand.Status.CANCELED)
                    .orElse(null);
        }
        return workCommandRepository
                .findFirstByMachine_MachineIdAndStatusOrderByCreatedAtDescCommandIdDesc(
                        machineId, WorkCommand.Status.CANCELED)
                .orElse(null);
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
                        && EnumSet.of(
                                WorkCommand.Status.ACCEPTED,
                                WorkCommand.Status.COMPLETED,
                                WorkCommand.Status.CANCELED)
                        .contains(command.getStatus());
        if (command.getStatus() == acknowledgedStatus || acceptedAlreadyProcessed) {
            if (acknowledgedStatus == WorkCommand.Status.ACCEPTED) {
                captureStartContext(command);
            }
            if (command.getAcknowledgedAt() == null) {
                command.setAcknowledgedAt(LocalDateTime.now());
            }
            if (command.getAckMessage() == null || command.getAckMessage().isBlank()) {
                command.setAckMessage(dto.getMessage());
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
        if (InspectionStandardService.supportsMeasurements(
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

    public boolean hasActiveExecution(String lotNo, String processCode) {
        return workCommandRepository
                .existsByLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusIn(
                        lotNo, processCode, WorkCommand.CommandType.START, ACTIVE_STATUSES)
                || workCommandRepository
                .existsByLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusIn(
                        lotNo, processCode, WorkCommand.CommandType.RESUME, ACTIVE_STATUSES);
    }

    public boolean hasStartedProcess(String lotNo, String processCode) {
        return workCommandRepository
                .existsByLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusIn(
                        lotNo, processCode, WorkCommand.CommandType.START,
                        STARTED_PROCESS_STATUSES);
    }

    public boolean hasActiveCommandForMachine(String machineId) {
        return workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                machineId, ACTIVE_STATUSES);
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
        return processRepository.findById(processCode).orElse(null);
    }

    private Optional<Machine> findAvailableMachineForUpdate(String processCode) {
        return machineRepository.findUsableByProcessForUpdate(processCode).stream()
                .filter(machine -> machine.getStatus() == Machine.Status.IDLE)
                .filter(machine -> !workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                        machine.getMachineId(), ACTIVE_STATUSES))
                .findFirst();
    }

    private WorkCommand buildStartCommand(
            Lot lot, Process process, Machine machine, int inputQty) {
        return WorkCommand.builder()
                .commandType(WorkCommand.CommandType.START)
                .machine(machine)
                .process(process)
                .lot(lot)
                .inputQty(inputQty)
                .build();
    }
}
