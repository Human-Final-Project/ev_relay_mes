package com.human.ev_relay_mes.feature.machine.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.machine.internal.repository.MachineRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineRegistryService implements MachineRegistry {

    private final MachineRepository machineRepository;

    @Override
    public Machine getRequiredMachine(String machineId) {
        return machineRepository.findById(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
    }

    @Override
    public Machine getRequiredMachineForUpdate(String machineId) {
        return machineRepository.findByIdForUpdate(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
    }

    @Override
    public List<Machine> getAllMachines() {
        return machineRepository.findAll();
    }

    @Override
    public List<Machine> getUsableMachinesForUpdate(String processCode) {
        return machineRepository.findUsableByProcessForUpdate(processCode);
    }
}
