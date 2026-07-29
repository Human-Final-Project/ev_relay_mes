package com.human.ev_relay_mes.feature.machine.api;

import java.util.List;

/**
 * 설비 현황과 Collector 상태 수신에 사용하는 공개 계약입니다.
 */
public interface MachineMonitoring {

    List<MachineResponseDto> getMachines();

    MachineResponseDto getMachine(String machineId);

    MachineStatusHistoryResponseDto updateStatus(MachineStatusReceiveRequestDto dto);

    List<MachineStatusHistoryResponseDto> getStatusHistory(String machineId);
}
