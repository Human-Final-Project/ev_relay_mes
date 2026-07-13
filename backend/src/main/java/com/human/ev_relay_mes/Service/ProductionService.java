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

    // L2 수집기가 전달한 공정별 투입·양품·불량 실적을 검증하고 생산 이력으로 저장할 때 사용한다.
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

    // 생산 실적 화면에서 LOT·설비·공정·상태·기간 조건으로 실적을 조회할 때 사용한다.
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

    // 생산 실적 상세 화면에서 단일 공정 실적의 상세 정보를 조회할 때 사용한다.
    public ProductionLogResponseDto getProductionLog(Long id) {
        return productionLogRepository.findById(id)
                .map(this::toResponse)
                .orElseThrow(() -> new CustomException(ErrorCode.PRODUCTION_LOG_NOT_FOUND));
    }

    // 생산 실적이 연결될 LOT가 실제 존재하는지 확인할 때 내부적으로 사용한다.
    private Lot findLot(String lotNo) {
        return entityManager.createQuery("select l from Lot l where l.lotNo = :lotNo", Lot.class)
                .setParameter("lotNo", lotNo)
                .getResultStream()
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    // 선택 검색 조건이 입력되지 않았는지 판단할 때 내부적으로 사용한다.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // 생산 실적 생성 시각이 사용자가 지정한 조회 기간에 포함되는지 판단할 때 사용한다.
    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    // 생산 실적 Entity를 생산 화면과 API에 전달할 응답 DTO로 변환할 때 사용한다.
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
