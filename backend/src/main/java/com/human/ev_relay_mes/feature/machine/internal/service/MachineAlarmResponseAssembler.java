package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmHistory;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmResponseDto;
import org.springframework.stereotype.Component;

@Component
class MachineAlarmResponseAssembler {

    MachineAlarmResponseDto toResponse(MachineAlarmHistory history) {
        Member clearer = history.getClearedBy();
        return MachineAlarmResponseDto.builder()
                .machineAlarmHistoryId(history.getMachineAlarmHistoryId())
                .machineId(history.getMachine().getMachineId())
                .machineName(history.getMachine().getMachineName())
                .alarmCode(history.getAlarmCode().getAlarmCode())
                .alarmName(history.getAlarmCode().getAlarmName())
                .alarmLevel(history.getAlarmLevel())
                .lotNo(history.getLot() == null
                        ? null : history.getLot().getLotNo())
                .processCode(history.getProcess() == null
                        ? null : history.getProcess().getProcessCode())
                .processName(history.getProcess() == null
                        ? null : history.getProcess().getProcessName())
                .occurredAt(history.getOccurredAt())
                .clearedAt(history.getClearedAt())
                .clearedById(clearer == null ? null : clearer.getMemberId())
                .clearedByName(clearer == null ? null : clearer.getMemberName())
                .message(history.getMessage())
                .cleared(history.getClearedAt() != null)
                .build();
    }
}
