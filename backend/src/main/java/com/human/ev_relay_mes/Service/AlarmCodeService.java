package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.AlarmCodeResponseDto;
import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.AlarmCodeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlarmCodeService {
    private final AlarmCodeRepository alarmCodeRepository;

    public List<AlarmCodeResponseDto> getAlarmCodes(String machineType) {
        return alarmCodeRepository.findAll().stream()
                .filter(code -> machineType == null || machineType.isBlank()
                        || code.getMachineType().equalsIgnoreCase(machineType))
                .sorted(Comparator.comparing(AlarmCode::getAlarmCode))
                .map(AlarmCodeResponseDto::fromEntity)
                .toList();
    }

    public AlarmCodeResponseDto getAlarmCode(String code) {
        return AlarmCodeResponseDto.fromEntity(alarmCodeRepository.findById(code)
                .orElseThrow(() -> new CustomException(ErrorCode.ALARM_CODE_NOT_FOUND)));
    }
}
