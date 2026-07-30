package com.human.ev_relay_mes.feature.collector.internal.service;

import com.human.ev_relay_mes.feature.collector.api.WorkCommand;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandResponseDto;
import com.human.ev_relay_mes.feature.collector.internal.repository.WorkCommandRepository;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.ProductionData;
import com.human.ev_relay_mes.feature.quality.api.QualityMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class WorkCommandResumeManager {

    private static final EnumSet<WorkCommand.Status> ACTIVE_STATUSES =
            EnumSet.of(
                    WorkCommand.Status.PENDING,
                    WorkCommand.Status.DISPATCHED,
                    WorkCommand.Status.ACCEPTED);
    private static final EnumSet<WorkCommand.Status> COMPLETABLE_STATUSES =
            EnumSet.of(
                    WorkCommand.Status.DISPATCHED,
                    WorkCommand.Status.ACCEPTED);

    private final WorkCommandRepository workCommandRepository;
    private final MachineRegistry machineRegistry;
    private final ProductionData productionData;
    private final QualityMetrics qualityMetrics;

    Optional<WorkCommandResponseDto> create(
            String machineId, String lotNo, String processCode) {
        Machine lockedMachine =
                machineRegistry.getRequiredMachineForUpdate(machineId);
        WorkCommand interrupted =
                findInterrupted(machineId, lotNo, processCode);
        if (interrupted == null
                || interrupted.getLot().getStatus() != Lot.Status.HOLD) {
            return Optional.empty();
        }

        Lot lot = interrupted.getLot();
        Process process = interrupted.getProcess();
        Machine machine = interrupted.getMachine();
        Optional<WorkCommand> existing = findActiveResume(
                machineId, lot.getLotNo(), process.getProcessCode());
        if (existing.isPresent()) {
            return Optional.of(WorkCommandResponseDto.fromEntity(existing.get()));
        }
        if (workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                machineId, ACTIVE_STATUSES)) {
            return Optional.empty();
        }

        int remainingQty = originalTargetQty(interrupted)
                - processedQuantity(lot, process);
        if (remainingQty <= 0) {
            return Optional.empty();
        }
        normalizeInterruptedMachineStatus(lockedMachine);
        WorkCommand resume = WorkCommand.builder()
                .commandType(WorkCommand.CommandType.RESUME)
                .machine(machine)
                .process(process)
                .lot(lot)
                .inputQty(remainingQty)
                .build();
        return Optional.of(WorkCommandResponseDto.fromEntity(
                workCommandRepository.save(resume)));
    }

    boolean hasHeldInterruptedWork(
            String machineId, String lotNo, String processCode) {
        WorkCommand interrupted =
                findInterrupted(machineId, lotNo, processCode);
        return interrupted != null
                && interrupted.getLot().getStatus() == Lot.Status.HOLD;
    }

    boolean activate(Lot lot, Process process, Machine machine) {
        List<WorkCommand> commands = workCommandRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndMachine_MachineIdAndCommandTypeAndStatusIn(
                        lot.getLotNo(),
                        process.getProcessCode(),
                        machine.getMachineId(),
                        WorkCommand.CommandType.RESUME,
                        COMPLETABLE_STATUSES);
        return !commands.isEmpty();
    }

    private WorkCommand findInterrupted(
            String machineId, String lotNo, String processCode) {
        if (lotNo != null && !lotNo.isBlank()
                && processCode != null && !processCode.isBlank()) {
            return workCommandRepository
                    .findFirstByMachine_MachineIdAndLot_LotNoAndProcess_ProcessCodeAndStatusOrderByCreatedAtDescCommandIdDesc(
                            machineId,
                            lotNo,
                            processCode,
                            WorkCommand.Status.CANCELED)
                    .orElse(null);
        }
        return workCommandRepository
                .findFirstByMachine_MachineIdAndStatusOrderByCreatedAtDescCommandIdDesc(
                        machineId, WorkCommand.Status.CANCELED)
                .orElse(null);
    }

    private Optional<WorkCommand> findActiveResume(
            String machineId, String lotNo, String processCode) {
        return workCommandRepository
                .findFirstByMachine_MachineIdAndLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusInOrderByCreatedAtDescCommandIdDesc(
                        machineId,
                        lotNo,
                        processCode,
                        WorkCommand.CommandType.RESUME,
                        ACTIVE_STATUSES);
    }

    private int processedQuantity(Lot lot, Process process) {
        int evaluatedQty = Math.toIntExact(
                qualityMetrics.countCompletedUnits(
                        lot.getLotNo(), process.getProcessCode()));
        int productionQty = productionData.sumInputQuantity(
                lot.getLotNo(), process.getProcessCode());
        return Math.max(evaluatedQty, productionQty);
    }

    private int originalTargetQty(WorkCommand interrupted) {
        return workCommandRepository
                .findByLot_LotNoOrderByCreatedAtAsc(
                        interrupted.getLot().getLotNo())
                .stream()
                .filter(command -> command.getCommandType()
                        == WorkCommand.CommandType.START)
                .filter(command -> command.getProcess().getProcessCode()
                        .equals(interrupted.getProcess().getProcessCode()))
                .filter(command -> command.getMachine().getMachineId()
                        .equals(interrupted.getMachine().getMachineId()))
                .map(WorkCommand::getInputQty)
                .findFirst()
                .orElse(interrupted.getInputQty());
    }

    private void normalizeInterruptedMachineStatus(Machine machine) {
        if (machine.getStatus() != Machine.Status.ERROR
                && machine.getStatus() != Machine.Status.STOPPED) {
            machine.setStatus(Machine.Status.ERROR);
        }
    }
}
