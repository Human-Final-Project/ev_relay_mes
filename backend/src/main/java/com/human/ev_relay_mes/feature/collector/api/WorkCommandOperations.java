package com.human.ev_relay_mes.feature.collector.api;

import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

public interface WorkCommandOperations {

    List<WorkCommandResponseDto> createInitialStartCommands(Lot lot);

    Optional<List<WorkCommandResponseDto>> tryCreateInitialStartCommands(Lot lot);

    Optional<WorkCommandResponseDto> tryCreateStartCommand(
            Lot lot, Process process, int inputQty);

    List<WorkCommandResponseDto> claimPendingCommands();

    List<WorkCommandResponseDto> claimPendingCommands(String machineId);

    void pauseForMachineError(String machineId);

    Optional<WorkCommandResponseDto> createResumeCommand(String machineId);

    Optional<WorkCommandResponseDto> createResumeCommand(
            String machineId, String lotNo, String processCode);

    boolean hasHeldInterruptedWork(String machineId, String lotNo, String processCode);

    boolean completeResumeCommand(Lot lot, Process process, Machine machine);

    WorkCommandResponseDto releaseDispatchedCommand(Long commandId, String machineId);

    WorkCommandResponseDto acknowledge(WorkCommandAckRequestDto dto);

    void completeStartCommand(Lot lot, Process process, Machine machine);

    int cancelActiveCommandsForLot(String lotNo);

    int cancelActiveCommandsForTerminalLots();

    List<WorkCommandResponseDto> getCommands(String lotNo);

    List<WorkCommand> findCommandsForLot(String lotNo);

    Optional<WorkCommand> findLatestCommandForMachine(
            String machineId, Collection<WorkCommand.Status> statuses);

    boolean hasActiveExecution(String lotNo, String processCode);

    boolean hasStartedProcess(String lotNo, String processCode);

    boolean hasActiveCommandForMachine(String machineId);
}
