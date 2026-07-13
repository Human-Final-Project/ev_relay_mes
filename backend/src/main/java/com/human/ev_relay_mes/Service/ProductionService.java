package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.ProductionLogSearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProductionLogResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionService {

    private final ProductionLogRepository productionLogRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final EntityManager entityManager;

    @Transactional
    public ProductionLogResponseDto saveResult(ProductionResultReceiveRequestDto dto) {
        if (dto.getInputQty() != dto.getOkQty() + dto.getNgQty()) {
            throw new CustomException(ErrorCode.PRODUCTION_QUANTITY_MISMATCH);
        }

        Lot lot = findLot(dto.getLotNo());
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        com.human.ev_relay_mes.Entity.Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));

        ProductionLog log = ProductionLog.builder()
                .lot(lot)
                .machine(machine)
                .process(process)
                .inputQty(dto.getInputQty())
                .okQty(dto.getOkQty())
                .ngQty(dto.getNgQty())
                .status(dto.getStatus())
                .startedAt(dto.getStartedAt())
                .endedAt(dto.getEndedAt())
                .build();
        return toResponse(productionLogRepository.save(log));
    }

    public List<ProductionLogResponseDto> search(ProductionLogSearchRequestDto condition) {
        return productionLogRepository.findAll().stream()
                .filter(log -> isBlank(condition.getLotNo()) || log.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(log -> isBlank(condition.getMachineId()) || log.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(log -> isBlank(condition.getProcessCode()) || log.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(log -> isBlank(condition.getStatus()) || log.getStatus().equalsIgnoreCase(condition.getStatus()))
                .filter(log -> isWithin(log.getCreatedAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    public ProductionLogResponseDto getProductionLog(Long id) {
        return productionLogRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCTION_LOG_NOT_FOUND));
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

    private ProductionLogResponseDto toResponse(ProductionLog log) {
        return ProductionLogResponseDto.builder()
                .productionLogId(log.getProductionLogId())
                .lotNo(log.getLot().getLotNo())
                .machineId(log.getMachine().getMachineId())
                .machineName(log.getMachine().getMachineName())
                .processCode(log.getProcess().getProcessCode())
                .processName(log.getProcess().getProcessName())
                .inputQty(log.getInputQty())
                .okQty(log.getOkQty())
                .ngQty(log.getNgQty())
                .status(log.getStatus())
                .startedAt(log.getStartedAt())
                .endedAt(log.getEndedAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
