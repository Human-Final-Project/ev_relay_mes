package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.feature.collector.api.WorkCommand;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineResponseDto;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusHistory;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.feature.production.api.ProductionData;
import com.human.ev_relay_mes.feature.quality.api.QualityMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
@RequiredArgsConstructor
class MachineResponseAssembler {

    private static final EnumSet<WorkCommand.Status> ACTIVE_COMMAND_STATUSES =
            EnumSet.of(
                    WorkCommand.Status.DISPATCHED,
                    WorkCommand.Status.ACCEPTED);

    private final WorkCommandOperations workCommandService;
    private final QualityMetrics qualityMetrics;
    private final ProductionData productionData;

    MachineResponseDto toMachineResponse(Machine machine) {
        MachineResponseDto.MachineResponseDtoBuilder builder =
                MachineResponseDto.builder()
                        .machineId(machine.getMachineId())
                        .machineName(machine.getMachineName())
                        .machineType(machine.getMachineType())
                        .processCode(machine.getProcess().getProcessCode())
                        .processName(machine.getProcess().getProcessName())
                        .status(machine.getStatus().name())
                        .createdAt(machine.getCreatedAt())
                        .updatedAt(machine.getUpdatedAt());
        if (machine.getStatus() == Machine.Status.RUNNING) {
            workCommandService
                    .findLatestCommandForMachine(
                            machine.getMachineId(), ACTIVE_COMMAND_STATUSES)
                    .ifPresent(command -> appendProgress(builder, command));
        }
        return builder.build();
    }

    MachineStatusHistoryResponseDto toHistoryResponse(
            MachineStatusHistory history) {
        return MachineStatusHistoryResponseDto.builder()
                .machineStatusHistoryId(history.getMachineStatusHistoryId())
                .machineId(history.getMachine().getMachineId())
                .machineName(history.getMachine().getMachineName())
                .status(history.getStatus().name())
                .lotNo(history.getLot() == null
                        ? null : history.getLot().getLotNo())
                .processCode(history.getProcess() == null
                        ? null : history.getProcess().getProcessCode())
                .processName(history.getProcess() == null
                        ? null : history.getProcess().getProcessName())
                .recordedAt(history.getRecordedAt())
                .message(history.getMessage())
                .build();
    }

    private void appendProgress(
            MachineResponseDto.MachineResponseDtoBuilder builder,
            WorkCommand command) {
        String lotNo = command.getLot().getLotNo();
        String processCode = command.getProcess().getProcessCode();
        int evaluatedQty = Math.toIntExact(
                qualityMetrics.countCompletedUnits(lotNo, processCode));
        int productionQty =
                productionData.sumInputQuantity(lotNo, processCode);
        int processedQty = Math.max(evaluatedQty, productionQty);
        int targetQty = targetQuantity(command, processedQty);
        int progressPercent = targetQty == 0
                ? 0 : Math.min(100, processedQty * 100 / targetQty);
        builder.currentLotNo(lotNo)
                .targetQty(targetQty)
                .processedQty(processedQty)
                .progressPercent(progressPercent);
    }

    private int targetQuantity(WorkCommand command, int processedQty) {
        if (command.getCommandType() != WorkCommand.CommandType.RESUME) {
            return command.getInputQty();
        }
        String lotNo = command.getLot().getLotNo();
        String processCode = command.getProcess().getProcessCode();
        return workCommandService.findCommandsForLot(lotNo).stream()
                .filter(item -> item.getCommandType()
                        == WorkCommand.CommandType.START)
                .filter(item -> item.getProcess().getProcessCode()
                        .equals(processCode))
                .mapToInt(WorkCommand::getInputQty)
                .findFirst()
                .orElse(processedQty + command.getInputQty());
    }
}
