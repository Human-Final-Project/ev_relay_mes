package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MachineService {

    private final MachineRepository machineRepository;
    private final MachineStatusHistoryRepository machineStatusHistoryRepository;
    private final ProcessRepository processRepository;
    private final EntityManager entityManager;

    public List<MachineResponseDto> getMachines() {
        return machineRepository.findAll().stream().map(this::toMachineResponse).toList();
    }

    public MachineResponseDto getMachine(String machineId) {
        return toMachineResponse(findMachine(machineId));
    }

    @Transactional
    public MachineStatusHistoryResponseDto updateStatus(MachineStatusReceiveRequestDto dto) {
        Machine machine = findMachine(dto.getMachineId());
        Machine.Status status = parseStatus(dto.getStatus());
        Lot lot = isBlank(dto.getLotNo()) ? null : findLot(dto.getLotNo());
        com.human.ev_relay_mes.Entity.Process process = isBlank(dto.getProcessCode()) ? null
                : processRepository.findById(dto.getProcessCode())
                        .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));

        machine.setStatus(status);
        MachineStatusHistory history = MachineStatusHistory.builder()
                .machine(machine)
                .status(status)
                .lot(lot)
                .process(process)
                .message(dto.getMessage())
                .build();
        return toHistoryResponse(machineStatusHistoryRepository.save(history));
    }

    public List<MachineStatusHistoryResponseDto> getStatusHistory(String machineId) {
        findMachine(machineId);
        return machineStatusHistoryRepository.findByMachine_MachineIdOrderByRecordedAtDesc(machineId)
                .stream().map(this::toHistoryResponse).toList();
    }

    private Machine findMachine(String machineId) {
        return machineRepository.findById(machineId)
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
    }

    private Lot findLot(String lotNo) {
        return entityManager.createQuery("select l from Lot l where l.lotNo = :lotNo", Lot.class)
                .setParameter("lotNo", lotNo)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private Machine.Status parseStatus(String status) {
        try {
            return Machine.Status.valueOf(status.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_MACHINE_STATUS);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private MachineResponseDto toMachineResponse(Machine machine) {
        return MachineResponseDto.builder()
                .machineId(machine.getMachineId())
                .machineName(machine.getMachineName())
                .machineType(machine.getMachineType())
                .processCode(machine.getProcess().getProcessCode())
                .processName(machine.getProcess().getProcessName())
                .status(machine.getStatus().name())
                .useYn(machine.getUseYn())
                .createdAt(machine.getCreatedAt())
                .updatedAt(machine.getUpdatedAt())
                .build();
    }

    private MachineStatusHistoryResponseDto toHistoryResponse(MachineStatusHistory history) {
        return MachineStatusHistoryResponseDto.builder()
                .machineStatusHistoryId(history.getMachineStatusHistoryId())
                .machineId(history.getMachine().getMachineId())
                .machineName(history.getMachine().getMachineName())
                .status(history.getStatus().name())
                .lotNo(history.getLot() == null ? null : history.getLot().getLotNo())
                .processCode(history.getProcess() == null ? null : history.getProcess().getProcessCode())
                .processName(history.getProcess() == null ? null : history.getProcess().getProcessName())
                .recordedAt(history.getRecordedAt())
                .message(history.getMessage())
                .build();
    }
}
