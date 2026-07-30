package com.human.ev_relay_mes.feature.collector.internal.service;

import com.human.ev_relay_mes.feature.collector.api.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandResponseDto;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.api.ProcessCodes;
import com.human.ev_relay_mes.feature.collector.api.WorkCommand;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.collector.internal.repository.WorkCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkCommandService implements WorkCommandOperations {

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
    private static final EnumSet<WorkCommand.Status> INTERRUPTIBLE_STATUSES = EnumSet.of(
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);

    private final WorkCommandRepository workCommandRepository;
    private final MachineRegistry machineRegistry;
    private final MasterDataLookup masterDataLookup;
    private final WorkCommandDispatcher dispatcher;
    private final WorkCommandAcknowledgementProcessor acknowledgementProcessor;
    private final WorkCommandResumeManager resumeManager;

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
        Process op20 = findActiveProcess(ProcessCodes.WINDING);
        Process op30 = findActiveProcess(ProcessCodes.CONTACT_WELDING);
        if (op20 == null || op30 == null) {
            throw new CustomException(ErrorCode.PROCESS_NOT_FOUND);
        }
        if (lot.getCurrentProcess() == null
                || !ProcessCodes.WINDING.equals(lot.getCurrentProcess().getProcessCode())) {
            return Optional.empty();
        }
        if (hasActiveExecution(lot.getLotNo(), ProcessCodes.WINDING)
                || hasActiveExecution(lot.getLotNo(), ProcessCodes.CONTACT_WELDING)) {
            return Optional.empty();
        }

        // 공정 코드 순서가 항상 같아 동시 스케줄링 시 설비 잠금 순서도 고정된다.
        Machine wind = findAvailableMachineForUpdate(ProcessCodes.WINDING).orElse(null);
        Machine weld = findAvailableMachineForUpdate(ProcessCodes.CONTACT_WELDING).orElse(null);
        if (wind == null || weld == null) {
            return Optional.empty();
        }

        WorkCommand windCommand = buildStartCommand(lot, op20, wind, lot.getInputQty());
        WorkCommand weldCommand = buildStartCommand(lot, op30, weld, lot.getInputQty());
        return Optional.of(List.of(
                WorkCommandResponseDto.fromEntity(workCommandRepository.save(windCommand)),
                WorkCommandResponseDto.fromEntity(workCommandRepository.save(weldCommand))));
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
        return dispatcher.claimPending(machineId);
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
        return resumeManager.create(machineId, lotNo, processCode);
    }

    public boolean hasHeldInterruptedWork(String machineId, String lotNo, String processCode) {
        return resumeManager.hasHeldInterruptedWork(
                machineId, lotNo, processCode);
    }

    @Transactional
    public boolean completeResumeCommand(Lot lot, Process process, Machine machine) {
        return resumeManager.complete(lot, process, machine);
    }

    @Transactional
    public WorkCommandResponseDto releaseDispatchedCommand(Long commandId, String machineId) {
        return dispatcher.release(commandId, machineId);
    }

    @Transactional
    public WorkCommandResponseDto acknowledge(WorkCommandAckRequestDto dto) {
        return acknowledgementProcessor.acknowledge(dto);
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

    /**
     * LOT이 종료될 때 아직 남아 있는 명령을 취소하여 IDLE 설비를 계속 점유하지 않게 한다.
     * 취소 뒤 늦은 ACCEPTED ACK가 도착해도 acknowledgeCommand가 CANCELED 상태를 되돌리지 않는다.
     */
    @Transactional
    public int cancelActiveCommandsForLot(String lotNo) {
        return cancelCommands(workCommandRepository.findByLotAndStatusInForUpdate(
                lotNo, ACTIVE_STATUSES));
    }

    /** 이벤트 유실이나 서버 재시작 전에 남은 종료 LOT의 유령 명령을 정리한다. */
    @Transactional
    public int cancelActiveCommandsForTerminalLots() {
        return cancelCommands(workCommandRepository.findActiveCommandsOfTerminalLotsForUpdate(
                EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED),
                ACTIVE_STATUSES));
    }

    private int cancelCommands(List<WorkCommand> commands) {
        LocalDateTime now = LocalDateTime.now();
        commands.forEach(command -> {
            command.setStatus(WorkCommand.Status.CANCELED);
            command.setCompletedAt(now);
        });
        return commands.size();
    }

    public List<WorkCommandResponseDto> getCommands(String lotNo) {
        return workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc(lotNo)
                .stream().map(WorkCommandResponseDto::fromEntity).toList();
    }

    @Override
    public List<WorkCommand> findCommandsForLot(String lotNo) {
        return workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc(lotNo);
    }

    @Override
    public Optional<WorkCommand> findLatestCommandForMachine(
            String machineId, Collection<WorkCommand.Status> statuses) {
        return workCommandRepository
                .findFirstByMachine_MachineIdAndStatusInOrderByCreatedAtDescCommandIdDesc(
                        machineId, statuses);
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

    private Process findActiveProcess(String processCode) {
        return masterDataLookup.findProcess(processCode).orElse(null);
    }

    private Optional<Machine> findAvailableMachineForUpdate(String processCode) {
        return machineRegistry.getUsableMachinesForUpdate(processCode).stream()
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
