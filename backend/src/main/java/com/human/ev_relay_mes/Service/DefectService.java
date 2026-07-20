package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.DefectHistorySearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectHistoryResponseDto;
import com.human.ev_relay_mes.Entity.DefectCode;
import com.human.ev_relay_mes.Entity.DefectHistory;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.DefectCodeRepository;
import com.human.ev_relay_mes.Repository.DefectHistoryRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
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
public class DefectService {

    private final DefectHistoryRepository defectHistoryRepository;
    private final DefectCodeRepository defectCodeRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final MemberRepository memberRepository;
    private final LotRepository lotRepository;

    // L2 수집기가 전달한 불량 발생 정보를 검증하고 불량 이력으로 저장할 때 사용한다.
    @Transactional
    public DefectHistoryResponseDto createDefect(DefectHistoryCreateRequestDto dto) {
        String eventId = normalizeEventId(dto.getEventId());
        if (eventId != null) {
            var existing = defectHistoryRepository.findByEventId(eventId);
            if (existing.isPresent()) {
                return toResponse(existing.get());
            }
        }
        Lot lot = lotRepository.findByLotNoForUpdate(dto.getLotNo())
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
        if (lot.getStatus() == Lot.Status.SCRAPPED) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS,
                    "폐기된 LOT에는 불량을 등록할 수 없습니다.");
        }
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        DefectCode defectCode = defectCodeRepository.findById(dto.getDefectCode())
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_CODE_NOT_FOUND));
        if (!"Y".equalsIgnoreCase(defectCode.getUseYn())) {
            throw new CustomException(ErrorCode.DEFECT_CODE_NOT_USABLE);
        }
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
    public List<DefectHistoryResponseDto> search(DefectHistorySearchRequestDto condition) {
        validateSearchPeriod(condition.getStartAt(), condition.getEndAt());
        return defectHistoryRepository.findAll(Sort.by(Sort.Direction.DESC, "occurredAt")).stream()
                .filter(item -> isBlank(condition.getLotNo()) || item.getLot().getLotNo().equals(condition.getLotNo()))
                .filter(item -> isBlank(condition.getMachineId()) || item.getMachine().getMachineId().equals(condition.getMachineId()))
                .filter(item -> isBlank(condition.getProcessCode()) || item.getProcess().getProcessCode().equals(condition.getProcessCode()))
                .filter(item -> isBlank(condition.getDefectCode()) || item.getDefectCode().getDefectCode().equals(condition.getDefectCode()))
                .filter(item -> condition.getConfirmed() == null
                        || condition.getConfirmed().equals(item.getConfirmedBy() != null))
                .filter(item -> isWithin(item.getOccurredAt(), condition.getStartAt(), condition.getEndAt()))
                .map(this::toResponse)
                .toList();
    }

    // 관리자가 미확인 불량을 확인 처리하고 확인자를 기록할 때 사용한다.
    @Transactional
    public DefectHistoryResponseDto confirmDefect(Long historyId, Long memberId) {
        DefectHistory history = defectHistoryRepository.findById(historyId)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_HISTORY_NOT_FOUND));
        if (history.getConfirmedBy() != null) {
            throw new CustomException(ErrorCode.DEFECT_ALREADY_CONFIRMED);
        }
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        history.setConfirmedBy(member);
        return toResponse(history);
    }

    // 불량 등록 요청의 LOT 번호가 실제 생산 LOT인지 확인할 때 내부적으로 사용한다.
    private Lot findLot(String lotNo) {
        return lotRepository.findByLotNo(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private void validateRelations(Machine machine, Process process, DefectCode defectCode) {
        if (!"Y".equalsIgnoreCase(machine.getUseYn())) {
            throw new CustomException(ErrorCode.MACHINE_NOT_USABLE);
        }
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
        int registeredQty = defectHistoryRepository
                .findByLot_LotNoOrderByOccurredAtDesc(lot.getLotNo()).stream()
                .mapToInt(DefectHistory::getDefectQty)
                .sum();
        int maximumQty = lot.getStatus() == Lot.Status.COMPLETED
                ? lot.getNgQty() : lot.getInputQty();
        if (registeredQty + newDefectQty > maximumQty) {
            throw new CustomException(ErrorCode.INVALID_DEFECT_QUANTITY,
                    "등록된 불량수량 합계가 LOT의 허용 불량수량을 초과합니다.");
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
        Member confirmer = history.getConfirmedBy();
        return DefectHistoryResponseDto.builder()
                .defectHistoryId(history.getDefectHistoryId())
                .lotNo(history.getLot().getLotNo())
                .machineId(history.getMachine().getMachineId())
                .machineName(history.getMachine().getMachineName())
                .processCode(history.getProcess().getProcessCode())
                .processName(history.getProcess().getProcessName())
                .defectCode(history.getDefectCode().getDefectCode())
                .defectName(history.getDefectCode().getDefectName())
                .defectQty(history.getDefectQty())
                .occurredAt(history.getOccurredAt())
                .message(history.getMessage())
                .confirmedById(confirmer == null ? null : confirmer.getMemberId())
                .confirmedByName(confirmer == null ? null : confirmer.getMemberName())
                .build();
    }
}
