package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.DefectCodeResponseDto;
import com.human.ev_relay_mes.Entity.DefectCode;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.DefectCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectCodeService {
    private final DefectCodeRepository defectCodeRepository;

    public List<DefectCodeResponseDto> getDefectCodes(String processCode) {
        return defectCodeRepository.findAll().stream()
                .filter(code -> processCode == null || processCode.isBlank()
                        || code.getProcess().getProcessCode().equalsIgnoreCase(processCode))
                .sorted(Comparator.comparing(DefectCode::getDefectCode))
                .map(DefectCodeResponseDto::fromEntity)
                .toList();
    }

    public DefectCodeResponseDto getDefectCode(String code) {
        return DefectCodeResponseDto.fromEntity(defectCodeRepository.findById(code)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_CODE_NOT_FOUND)));
    }
}
