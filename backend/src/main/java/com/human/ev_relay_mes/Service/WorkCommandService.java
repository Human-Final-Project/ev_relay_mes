package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.Dto.Response.WorkCommandResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.WorkCommand;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.WorkCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkCommandService {

    private static final String PARALLEL_PROCESS_1 = "OP20";
    private static final String PARALLEL_PROCESS_2 = "OP30";

    private static final EnumSet<WorkCommand.Status> ACTIVE_STATUSES = EnumSet.of(
            WorkCommand.Status.PENDING,
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);
    private static final EnumSet<WorkCommand.Status> MACHINE_RESERVED_STATUSES = EnumSet.of(
            WorkCommand.Status.DISPATCHED,
            WorkCommand.Status.ACCEPTED);

    private final WorkCommandRepository workCommandRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;

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
        List<WorkCommand> pending = workCommandRepository
                .findByStatusForUpdate(WorkCommand.Status.PENDING);
        LocalDateTime now = LocalDateTime.now();

        return pending.stream()
                .filter(command -> command.getMachine().getStatus() == Machine.Status.IDLE)
                .filter(command -> !workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                        command.getMachine().getMachineId(), MACHINE_RESERVED_STATUSES))
                .map(command -> {
                    command.setStatus(WorkCommand.Status.DISPATCHED);
                    command.setDispatchedAt(now);
                    return WorkCommandResponseDto.fromEntity(command);
                })
                .toList();
    }

    @Transactional
    public WorkCommandResponseDto acknowledge(WorkCommandAckRequestDto dto) {
        WorkCommand command = workCommandRepository.findByIdForUpdate(dto.getCommandId())
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_COMMAND_NOT_FOUND));
        if (!command.getMachine().getMachineId().equals(dto.getMachineId())) {
            throw new CustomException(ErrorCode.WORK_COMMAND_MACHINE_MISMATCH);
        }
        if (command.getStatus() != WorkCommand.Status.DISPATCHED) {
            throw new CustomException(ErrorCode.INVALID_WORK_COMMAND_STATUS);
        }

        command.setStatus(WorkCommand.Status.valueOf(dto.getAckStatus().toUpperCase()));
        command.setAckMessage(dto.getMessage());
        command.setAcknowledgedAt(LocalDateTime.now());
        return WorkCommandResponseDto.fromEntity(command);
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

    private Process activeProcess(String processCode) {
        return processRepository.findById(processCode)
                .filter(process -> "Y".equalsIgnoreCase(process.getUseYn()))
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
