package com.human.ev_relay_mes.feature.collector.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.collector.api.WorkCommand;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandResponseDto;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.collector.internal.repository.WorkCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
class WorkCommandDispatcher {

    private static final long ACK_TIMEOUT_SECONDS = 10L;
    private static final EnumSet<WorkCommand.Status> RESERVED_STATUSES =
            EnumSet.of(WorkCommand.Status.DISPATCHED, WorkCommand.Status.ACCEPTED);

    private final WorkCommandRepository workCommandRepository;

    List<WorkCommandResponseDto> claimPending(String machineId) {
        String normalizedMachineId = machineId == null ? null : machineId.trim();
        LocalDateTime now = LocalDateTime.now();
        requeueStale(normalizedMachineId, now.minusSeconds(ACK_TIMEOUT_SECONDS));

        List<WorkCommand> pending =
                normalizedMachineId == null || normalizedMachineId.isBlank()
                        ? workCommandRepository.findByStatusForUpdate(
                        WorkCommand.Status.PENDING)
                        : workCommandRepository.findByMachineAndStatusForDispatch(
                        normalizedMachineId, WorkCommand.Status.PENDING);
        return reserveDispatchable(pending, now);
    }

    WorkCommandResponseDto release(Long commandId, String machineId) {
        WorkCommand command = workCommandRepository.findByIdForUpdate(commandId)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.WORK_COMMAND_NOT_FOUND));
        if (machineId == null || machineId.isBlank()
                || !command.getMachine().getMachineId().equals(machineId.trim())) {
            throw new CustomException(ErrorCode.WORK_COMMAND_MACHINE_MISMATCH);
        }
        if (command.getStatus() == WorkCommand.Status.PENDING) {
            return WorkCommandResponseDto.fromEntity(command);
        }
        if (command.getStatus() != WorkCommand.Status.DISPATCHED) {
            throw new CustomException(
                    ErrorCode.INVALID_WORK_COMMAND_STATUS,
                    "L1 전송 전에는 DISPATCHED 상태의 명령만 반환할 수 있습니다.");
        }
        command.setStatus(WorkCommand.Status.PENDING);
        command.setDispatchedAt(null);
        return WorkCommandResponseDto.fromEntity(command);
    }

    private List<WorkCommandResponseDto> reserveDispatchable(
            List<WorkCommand> pending, LocalDateTime now) {
        List<WorkCommandResponseDto> claimed = new ArrayList<>();
        Set<String> reservedDuringClaim = new HashSet<>();
        for (WorkCommand command : pending) {
            String machineId = command.getMachine().getMachineId();
            if (!canDispatch(command)
                    || reservedDuringClaim.contains(machineId)
                    || workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                    machineId, RESERVED_STATUSES)) {
                continue;
            }
            command.setStatus(WorkCommand.Status.DISPATCHED);
            command.setDispatchedAt(now);
            reservedDuringClaim.add(machineId);
            claimed.add(WorkCommandResponseDto.fromEntity(command));
        }
        return claimed;
    }

    private void requeueStale(String machineId, LocalDateTime cutoff) {
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

    private boolean canDispatch(WorkCommand command) {
        Machine.Status machineStatus = command.getMachine().getStatus();
        if (command.getCommandType() == WorkCommand.CommandType.RESUME) {
            return machineStatus == Machine.Status.ERROR
                    || machineStatus == Machine.Status.STOPPED;
        }
        return machineStatus == Machine.Status.IDLE;
    }
}
