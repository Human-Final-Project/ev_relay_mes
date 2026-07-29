package com.human.ev_relay_mes.feature.machine.api;

import java.util.List;

/**
 * 설비 엔티티가 필요한 다른 기능에 저장소 대신 제공하는 공개 계약입니다.
 */
public interface MachineRegistry {

    Machine getRequiredMachine(String machineId);

    Machine getRequiredMachineForUpdate(String machineId);

    List<Machine> getAllMachines();

    List<Machine> getUsableMachinesForUpdate(String processCode);
}
