package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.DefectHistorySearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectHistoryResponseDto;
import com.human.ev_relay_mes.Entity.DefectCode;
import com.human.ev_relay_mes.Entity.DefectHistory;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.DefectCodeRepository;
import com.human.ev_relay_mes.Repository.DefectHistoryRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
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
public class DefectService {

    private final DefectHistoryRepository defectHistoryRepository;
    private final DefectCodeRepository defectCodeRepository;
    private final MachineRepository machineRepository;
    private final ProcessRepository processRepository;
    private final MemberRepository memberRepository;
    private final EntityManager entityManager;

    @Transactional
    public DefectHistoryResponseDto createDefect(DefectHistoryCreateRequestDto dto) {
        Lot lot = findLot(dto.getLotNo());
        Machine machine = machineRepository.findById(dto.getMachineId())
                .orElseThrow(() -> new CustomException(ErrorCode.MACHINE_NOT_FOUND));
        com.human.ev_relay_mes.Entity.Process process = processRepository.findById(dto.getProcessCode())
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        DefectCode defectCode = defectCodeRepository.findById(dto.getDefectCode())
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_CODE_NOT_FOUND));
        if (!"Y".equalsIgnoreCase(defectCode.getUseYn())) {
            throw new CustomException(ErrorCode.DEFECT_CODE_NOT_USABLE);
        }

        DefectHistory history = DefectHistory.builder()
                .lot(lot)
                .machine(machine)
                .process(process)
                .defectCode(defectCode)
                .defectQty(dto.getDefectQty())
                .message(dto.getMessage())
                .build();
        return toResponse(defectHistoryRepository.save(history));
    }

    public List<DefectHistoryResponseDto> search(DefectHistorySearchRequestDto condition) {
        return defectHistoryRepository.findAll().stream()
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
