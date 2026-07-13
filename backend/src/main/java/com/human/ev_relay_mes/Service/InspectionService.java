package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionSearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.InspectionResponseDto;
import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.InspectionRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InspectionService {

    private final InspectionRepository inspectionRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final EntityManager entityManager;

    @Transactional
    public InspectionResponseDto saveResult(InspectionResultReceiveRequestDto dto) {
        if (dto.getLowerLimit() != null && dto.getUpperLimit() != null
                && dto.getLowerLimit().compareTo(dto.getUpperLimit()) > 0) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_LIMIT);
        }

        Inspection.Result result;
        try {
            result = Inspection.Result.valueOf(dto.getResult().toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_RESULT);
        }

        Lot lot = findLot(dto.getLotNo());
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        com.human.ev_relay_mes.Entity.Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        Inspection inspection = Inspection.builder()
                .lot(lot)
                .machine(machine)
                .process(process)
                .inspectionItem(dto.getInspectionItem())
                .measuredValue(dto.getMeasuredValue())
                .unit(dto.getUnit())
                .lowerLimit(dto.getLowerLimit())
                .upperLimit(dto.getUpperLimit())
                .result(result)
                .build();
        return toResponse(inspectionRepository.save(inspection));
    }

    public List<InspectionResponseDto> search(InspectionSearchRequestDto condition) {
        return inspectionRepository.findAll().stream()
                .filter(item -> isBlank(condition.getLotNo()) || item.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(item -> isBlank(condition.getMachineId()) || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> isBlank(condition.getProcessCode()) || item.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(item -> isBlank(condition.getResult()) || item.getResult().name().equalsIgnoreCase(condition.getResult()))
                .filter(item -> isWithin(item.getInspectedAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    private Lot findLot(String lotNo) {
        return entityManager.createQuery("select l from Lot l where l.lotNo = :lotNo", Lot.class)
                .setParameter("lotNo", lotNo)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    private InspectionResponseDto toResponse(Inspection inspection) {
        return InspectionResponseDto.builder()
                .inspectionId(inspection.getInspectionId())
                .lotNo(inspection.getLot().getLotNo())
                .machineId(inspection.getMachine().getMachineId())
                .machineName(inspection.getMachine().getMachineName())
                .processCode(inspection.getProcess().getProcessCode())
                .processName(inspection.getProcess().getProcessName())
                .inspectionItem(inspection.getInspectionItem())
                .measuredValue(inspection.getMeasuredValue())
                .unit(inspection.getUnit())
                .lowerLimit(inspection.getLowerLimit())
                .upperLimit(inspection.getUpperLimit())
                .result(inspection.getResult().name())
                .inspectedAt(inspection.getInspectedAt())
                .build();
    }
}
