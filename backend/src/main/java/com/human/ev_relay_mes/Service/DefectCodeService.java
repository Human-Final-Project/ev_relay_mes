package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.DefectCodeRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectCodeResponseDto;
import com.human.ev_relay_mes.Entity.DefectCode;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.DefectCodeRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefectCodeService {

    private final DefectCodeRepository defectCodeRepository;
    private final ProcessRepository processRepository;

    public List<DefectCodeResponseDto> getDefectCodes(String processCode, Boolean activeOnly) {
        List<DefectCode> codes;
        if (processCode != null && !processCode.isBlank()) {
            codes = Boolean.FALSE.equals(activeOnly)
                    ? defectCodeRepository.findAll().stream()
                            .filter(code -> code.getProcess().getProcessCode().equals(processCode)).toList()
                    : defectCodeRepository.findByProcess_ProcessCodeAndUseYnOrderByDefectCodeAsc(processCode, "Y");
        } else {
            codes = Boolean.FALSE.equals(activeOnly)
                    ? defectCodeRepository.findAll()
                    : defectCodeRepository.findByUseYnOrderByDefectCodeAsc("Y");
        }
        return codes.stream().map(DefectCodeResponseDto::fromEntity).toList();
    }

    public DefectCodeResponseDto getDefectCode(String code) {
        return DefectCodeResponseDto.fromEntity(findDefectCode(code));
    }

    @Transactional
    public DefectCodeResponseDto createDefectCode(DefectCodeRequestDto dto) {
        if (defectCodeRepository.existsByDefectCode(dto.getDefectCode())) {
            throw new CustomException(ErrorCode.DUPLICATE_DEFECT_CODE);
        }
        DefectCode defectCode = DefectCode.builder()
                .defectCode(dto.getDefectCode())
                .defectName(dto.getDefectName())
                .process(findProcess(dto.getProcessCode()))
                .description(dto.getDescription())
                .build();
        return DefectCodeResponseDto.fromEntity(defectCodeRepository.save(defectCode));
    }

    @Transactional
    public DefectCodeResponseDto updateDefectCode(String code, DefectCodeRequestDto dto) {
        if (!code.equals(dto.getDefectCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "경로와 요청 본문의 불량 코드가 다릅니다.");
        }
        DefectCode defectCode = findDefectCode(code);
        defectCode.setDefectName(dto.getDefectName());
        defectCode.setProcess(findProcess(dto.getProcessCode()));
        defectCode.setDescription(dto.getDescription());
        return DefectCodeResponseDto.fromEntity(defectCode);
    }

    @Transactional
    public DefectCodeResponseDto updateUseYn(String code, boolean active) {
        DefectCode defectCode = findDefectCode(code);
        defectCode.setUseYn(active ? "Y" : "N");
        return DefectCodeResponseDto.fromEntity(defectCode);
    }

    @Transactional
    public void deleteDefectCode(String code) {
        defectCodeRepository.delete(findDefectCode(code));
    }

    private DefectCode findDefectCode(String code) {
        return defectCodeRepository.findById(code)
                .orElseThrow(() -> new CustomException(ErrorCode.DEFECT_CODE_NOT_FOUND));
    }

    private Process findProcess(String code) {
        return processRepository.findById(code)
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
    }
}
