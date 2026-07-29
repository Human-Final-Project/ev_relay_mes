package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.feature.quality.api.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.feature.quality.api.DefectHistorySearchRequestDto;
import com.human.ev_relay_mes.feature.quality.api.DefectHistoryResponseDto;
import com.human.ev_relay_mes.feature.masterdata.api.DefectCode;
import com.human.ev_relay_mes.feature.quality.api.DefectHistory;
import com.human.ev_relay_mes.feature.quality.api.DefectOperations;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.quality.internal.repository.DefectHistoryRepository;
import com.human.ev_relay_mes.feature.production.api.ProductionData;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectService implements DefectOperations {

    private final DefectHistoryRepository defectHistoryRepository;
    private final MachineRegistry machineRegistry;
    private final MasterDataLookup masterDataLookup;
    private final ProductionData productionData;

    // L2 수집기가 전달한 불량 발생 정보를 검증하고 불량 이력으로 저장할 때 사용한다.
    @Transactional
    @Override
    public DefectHistoryResponseDto createDefect(DefectHistoryCreateRequestDto dto) {
        String eventId = normalizeEventId(dto.getEventId());
        if (eventId != null) {
            var existing = defectHistoryRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        Lot lot = productionData.getRequiredLotForUpdate(dto.getLotNo());
        if (lot.getStatus() == Lot.Status.SCRAPPED) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS,
                    "폐기된 LOT에는 불량을 등록할 수 없습니다.");
        }
        Machine machine = machineRegistry.getRequiredMachine(dto.getMachineId());
        Process process = masterDataLookup.getRequiredProcess(dto.getProcessCode());
        DefectCode defectCode = masterDataLookup.getRequiredDefectCode(dto.getDefectCode());
        validateRelations(machine, process, defectCode);
        validateDefectQuantity(lot, dto.getDefectQty());

        DefectHistory history = DefectHistory.builder()
                .eventId(eventId)
                .lot(lot)
                .machine(machine)
                .process(process)
                .defectCode(defectCode)
                .defectQty(dto.getDefectQty())
                .message(dto.getMessage())
                .build();
        return toResponse(defectHistoryRepository.save(history));
    }

    // 불량 관리 화면에서 LOT·설비·공정·불량 코드·확인 여부·기간 조건으로 이력을 조회할 때 사용한다.
    @Override
    public List<DefectHistoryResponseDto> search(DefectHistorySearchRequestDto condition) {
        validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return defectHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "occurredAt")).stream()
                .filter(item -> condition.getWorkOrderId() == null
                        || item.getLot().getWorkOrder().getWorkOrderId().equals(condition.getWorkOrderId()))
                .filter(item -> isBlank(condition.getLotNo()) || item.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(item -> isBlank(condition.getMachineId()) || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> isBlank(condition.getProcessCode()) || item.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(item -> isBlank(condition.getDefectCode()) || item.getDefectCode().getDefectCode().equals(condition.getDefectCode()))
                .filter(item -> isWithin(item.getOccurredAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    private Lot findLot(String lotNo) {
        return productionData.getRequiredLot(lotNo);
    }

    private void validateRelations(Machine machine, Process process, DefectCode defectCode) {
        if (!machine.getProcess().getProcessCode().equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "설비에 지정된 공정과 불량 발생 공정이 일치하지 않습니다.");
        }
        if (!defectCode.getProcess().getProcessCode().equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "불량 코드에 지정된 공정과 불량 발생 공정이 일치하지 않습니다.");
        }
    }

    private void validateDefectQuantity(Lot lot, int newDefectQty) {
        int maximumQty = lot.getStatus() == Lot.Status.COMPLETED
                ? lot.getNgQty() : lot.getInputQty();
        if (newDefectQty > maximumQty) {
            throw new CustomException(ErrorCode.INVALID_DEFECT_QUANTITY,
                    "개별 불량수량이 LOT의 허용 수량을 초과합니다.");
        }
    }

    private void validateSearchPeriod(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "조회 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    // 선택 검색 조건이 입력되지 않았는지 판단할 때 내부적으로 사용한다.
    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeEventId(String eventId) {
        return isBlank(eventId) ? null : eventId.trim();
    }

    // 불량 발생 시각이 사용자가 지정한 조회 기간에 포함되는지 판단할 때 사용한다.
    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    // 불량 이력 Entity를 화면과 API에 전달할 응답 DTO로 변환할 때 사용한다.
    private DefectHistoryResponseDto toResponse(DefectHistory history) {
        return DefectHistoryResponseDto.builder()
                .defectHistoryId(history.getDefectHistoryId())
                .lotNo(history.getLot().getLotNo())
                .machineId(history.getMachine().getMachineId())
                .machineName(history.getMachine().getMachineName())
                .processCode(history.getProcess().getProcessCode())
                .processName(history.getProcess().getProcessName())
                .defectCode(history.getDefectCode().getDefectCode())
                .defectName(history.getDefectCode().getDefectName())
                .defectDescription(history.getDefectCode().getDescription())
                .defectQty(history.getDefectQty())
                .occurredAt(history.getOccurredAt())
                .message(history.getMessage())
                .build();
    }
}
