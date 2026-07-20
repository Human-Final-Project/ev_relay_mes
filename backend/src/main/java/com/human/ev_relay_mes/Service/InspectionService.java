package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionSearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.InspectionResponseDto;
import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.InspectionRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
    private final LotRepository lotRepository;

    // L2 수집기가 전달한 검사 측정값과 판정 결과를 검사 이력으로 저장할 때 사용한다.
    @Transactional
    public InspectionResponseDto saveResult(InspectionResultReceiveRequestDto dto) {
        String eventId = normalizeEventId(dto.getEventId());
        if (eventId != null) {
            var existing = inspectionRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        validateLimits(dto);
        Inspection.Result result = parseResult(dto.getResult());
        validateMeasuredResult(dto, result);

        Lot lot = findLot(dto.getLotNo());
        if (lot.getStatus() == Lot.Status.SCRAPPED) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS,
                    "폐기된 LOT에는 검사결과를 등록할 수 없습니다.");
        }
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        validateMachineAndProcess(machine, process);
        Inspection inspection = Inspection.builder()
                .eventId(eventId)
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

    // 검사 결과 화면에서 LOT·설비·공정·판정·기간 조건으로 검사 이력을 조회할 때 사용한다.
    public List<InspectionResponseDto> search(InspectionSearchRequestDto condition) {
        validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return inspectionRepository.findAll(Sort.by(Sort.Direction.DESC, "inspectedAt")).stream()
                .filter(item -> isBlank(condition.getLotNo()) || item.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(item -> isBlank(condition.getMachineId()) || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> isBlank(condition.getProcessCode()) || item.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(item -> isBlank(condition.getResult()) || item.getResult().name().equalsIgnoreCase(condition.getResult()))
                .filter(item -> isWithin(item.getInspectedAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    // 검사 결과가 연결될 생산 LOT를 LOT 번호로 확인할 때 내부적으로 사용한다.
    private Lot findLot(String lotNo) {
        return lotRepository.findByLotNo(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private void validateLimits(InspectionResultReceiveRequestDto dto) {
        if (dto.getLowerLimit() != null && dto.getUpperLimit() != null
                && dto.getLowerLimit().compareTo(dto.getUpperLimit()) > 0) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_LIMIT);
        }
        if (dto.getMeasuredValue() == null
                && (dto.getLowerLimit() != null || dto.getUpperLimit() != null)) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_VALUE,
                    "검사 기준값이 있으면 측정값이 필요합니다.");
        }
    }

    private Inspection.Result parseResult(String result) {
        try {
            return Inspection.Result.valueOf(result.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_RESULT);
        }
    }

    private void validateMeasuredResult(
            InspectionResultReceiveRequestDto dto, Inspection.Result result) {
        if (dto.getMeasuredValue() == null) {
            return;
        }
        boolean belowLower = dto.getLowerLimit() != null
                && dto.getMeasuredValue().compareTo(dto.getLowerLimit()) < 0;
        boolean aboveUpper = dto.getUpperLimit() != null
                && dto.getMeasuredValue().compareTo(dto.getUpperLimit()) > 0;
        Inspection.Result expected = belowLower || aboveUpper
                ? Inspection.Result.NG : Inspection.Result.OK;
        if ((dto.getLowerLimit() != null || dto.getUpperLimit() != null) && result != expected) {
            throw new CustomException(ErrorCode.INVALID_INSPECTION_RESULT,
                    "측정값과 검사 판정 결과가 일치하지 않습니다.");
        }
    }

    private void validateMachineAndProcess(Machine machine, Process process) {
        if (!"Y".equalsIgnoreCase(machine.getUseYn())) {
            throw new CustomException(ErrorCode.MACHINE_NOT_USABLE);
        }
        if (!machine.getProcess().getProcessCode().equals(process.getProcessCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "설비에 지정된 공정과 검사 공정이 일치하지 않습니다.");
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

    // 검사 시각이 사용자가 지정한 조회 기간에 포함되는지 판단할 때 사용한다.
    private boolean isWithin(LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start)) && (end == null || !value.isAfter(end));
    }

    // 검사 Entity를 검사 결과 화면과 API에 전달할 응답 DTO로 변환할 때 사용한다.
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
