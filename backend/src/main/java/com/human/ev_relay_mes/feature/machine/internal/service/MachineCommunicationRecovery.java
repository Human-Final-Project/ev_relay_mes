package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmHistory;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class MachineCommunicationRecovery {

    private final MachineAlarmHistoryRepository alarmHistoryRepository;
    private final WorkCommandOperations workCommandService;

    void synchronize(
            Machine machine, Machine.Status reportedStatus,
            Lot lot, Process process) {
        clearCommunicationAlarms(machine.getMachineId());
        if (reportedStatus == Machine.Status.ERROR
                && lot != null
                && process != null
                && lot.getStatus() == Lot.Status.HOLD
                && !hasActiveEquipmentError(machine.getMachineId())) {
            workCommandService.createResumeCommand(
                    machine.getMachineId(),
                    lot.getLotNo(),
                    process.getProcessCode());
        }
    }

    private void clearCommunicationAlarms(String machineId) {
        LocalDateTime recoveredAt = LocalDateTime.now();
        activeAlarms(machineId).filter(this::isCommunicationAlarm)
                .forEach(history -> history.setClearedAt(recoveredAt));
    }

    private boolean hasActiveEquipmentError(String machineId) {
        return activeAlarms(machineId)
                .filter(history -> "ERROR".equalsIgnoreCase(
                        history.getAlarmLevel()))
                .anyMatch(history -> !isCommunicationAlarm(history));
    }

    private java.util.stream.Stream<MachineAlarmHistory> activeAlarms(
            String machineId) {
        return alarmHistoryRepository
                .findByMachine_MachineIdAndClearedAtIsNullOrderByOccurredAtAsc(
                        machineId)
                .stream();
    }

    private boolean isCommunicationAlarm(MachineAlarmHistory history) {
        String code = history.getAlarmCode().getAlarmCode();
        return "COMM_DISCONNECTED".equals(code)
                || "COMM_TIMEOUT".equals(code);
    }
}
