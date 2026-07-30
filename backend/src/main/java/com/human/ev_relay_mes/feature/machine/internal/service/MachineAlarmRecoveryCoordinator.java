package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmHistory;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusHistory;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.feature.production.api.ProductionSchedulingRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MachineAlarmRecoveryCoordinator {

    private final MachineAlarmHistoryRepository alarmHistoryRepository;
    private final MachineStatusHistoryRepository statusHistoryRepository;
    private final WorkCommandOperations workCommandService;
    private final ProductionSchedulingRequests schedulingRequests;

    void recoverAfterClear(MachineAlarmHistory clearedHistory) {
        if (!"ERROR".equalsIgnoreCase(clearedHistory.getAlarmLevel())) {
            return;
        }
        Machine machine = clearedHistory.getMachine();
        if (hasAnotherBlockingError(clearedHistory)) {
            return;
        }

        String lotNo = clearedHistory.getLot() == null
                ? null : clearedHistory.getLot().getLotNo();
        String processCode = clearedHistory.getProcess() == null
                ? null : clearedHistory.getProcess().getProcessCode();
        if (workCommandService.createResumeCommand(
                machine.getMachineId(), lotNo, processCode).isPresent()) {
            return;
        }
        if (workCommandService.hasHeldInterruptedWork(
                machine.getMachineId(), lotNo, processCode)) {
            return;
        }
        if (!isCommunicationAlarm(clearedHistory)) {
            changeStatusToIdle(machine);
            schedulingRequests.requestMachine(machine.getMachineId());
        }
    }

    private boolean hasAnotherBlockingError(
            MachineAlarmHistory clearedHistory) {
        return alarmHistoryRepository
                .findActiveByMachineForUpdate(
                        clearedHistory.getMachine().getMachineId())
                .stream()
                .filter(item -> !item.getMachineAlarmHistoryId()
                        .equals(clearedHistory.getMachineAlarmHistoryId()))
                .filter(item -> "ERROR".equalsIgnoreCase(
                        item.getAlarmLevel()))
                .anyMatch(item -> !isCommunicationAlarm(item));
    }

    private boolean isCommunicationAlarm(MachineAlarmHistory history) {
        String code = history.getAlarmCode().getAlarmCode();
        return "COMM_DISCONNECTED".equals(code)
                || "COMM_TIMEOUT".equals(code);
    }

    private void changeStatusToIdle(Machine machine) {
        if (machine.getStatus() == Machine.Status.IDLE) {
            return;
        }
        machine.setStatus(Machine.Status.IDLE);
        statusHistoryRepository.save(MachineStatusHistory.builder()
                .machine(machine)
                .status(Machine.Status.IDLE)
                .process(machine.getProcess())
                .message("알람 해제 후 대기 상태 복구")
                .build());
    }
}
