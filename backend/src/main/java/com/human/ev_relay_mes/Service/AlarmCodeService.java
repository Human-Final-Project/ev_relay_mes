package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.AlarmCodeRequestDto;
import com.human.ev_relay_mes.Dto.Response.AlarmCodeResponseDto;
import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.AlarmCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmCodeService {

    private final AlarmCodeRepository alarmCodeRepository;

    public List<AlarmCodeResponseDto> getAlarmCodes(String machineType, Boolean activeOnly) {
        List<AlarmCode> codes;
        if (machineType != null && !machineType.isBlank()) {
            codes = Boolean.FALSE.equals(activeOnly)
                    ? alarmCodeRepository.findAll().stream()
                            .filter(code -> code.getMachineType().equalsIgnoreCase(machineType)).toList()
                    : alarmCodeRepository.findByMachineTypeAndUseYnOrderByAlarmCodeAsc(machineType, "Y");
        } else {
            codes = Boolean.FALSE.equals(activeOnly)
                    ? alarmCodeRepository.findAll()
                    : alarmCodeRepository.findByUseYnOrderByAlarmCodeAsc("Y");
        }
        return codes.stream().map(AlarmCodeResponseDto::fromEntity).toList();
    }

    public AlarmCodeResponseDto getAlarmCode(String code) {
        return AlarmCodeResponseDto.fromEntity(findAlarmCode(code));
    }

    @Transactional
    public AlarmCodeResponseDto createAlarmCode(AlarmCodeRequestDto dto) {
        if (alarmCodeRepository.existsByAlarmCode(dto.getAlarmCode())) {
            throw new CustomException(ErrorCode.DUPLICATE_ALARM_CODE);
        }
        AlarmCode alarmCode = AlarmCode.builder()
                .alarmCode(dto.getAlarmCode())
                .alarmName(dto.getAlarmName())
                .machineType(dto.getMachineType())
                .description(dto.getDescription())
                .build();
        return AlarmCodeResponseDto.fromEntity(alarmCodeRepository.save(alarmCode));
    }

    @Transactional
    public AlarmCodeResponseDto updateAlarmCode(String code, AlarmCodeRequestDto dto) {
        if (!code.equals(dto.getAlarmCode())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "경로와 요청 본문의 알람 코드가 다릅니다.");
        }
        AlarmCode alarmCode = findAlarmCode(code);
        alarmCode.setAlarmName(dto.getAlarmName());
        alarmCode.setMachineType(dto.getMachineType());
        alarmCode.setDescription(dto.getDescription());
        return AlarmCodeResponseDto.fromEntity(alarmCode);
    }

    @Transactional
    public AlarmCodeResponseDto updateUseYn(String code, boolean active) {
        AlarmCode alarmCode = findAlarmCode(code);
        alarmCode.setUseYn(active ? "Y" : "N");
        return AlarmCodeResponseDto.fromEntity(alarmCode);
    }

    @Transactional
    public void deleteAlarmCode(String code) {
        alarmCodeRepository.delete(findAlarmCode(code));
    }

    private AlarmCode findAlarmCode(String code) {
        return alarmCodeRepository.findById(code)
                .orElseThrow(() -> new CustomException(ErrorCode.ALARM_CODE_NOT_FOUND));
    }
}
