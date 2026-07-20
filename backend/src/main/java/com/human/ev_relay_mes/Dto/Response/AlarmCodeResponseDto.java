package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.AlarmCode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AlarmCodeResponseDto {

    private String alarmCode;
    private String alarmName;
    private String machineType;
    private String description;
    private LocalDateTime createdAt;

    public static AlarmCodeResponseDto fromEntity(AlarmCode alarmCode) {
        return AlarmCodeResponseDto.builder()
                .alarmCode(alarmCode.getAlarmCode())
                .alarmName(alarmCode.getAlarmName())
                .machineType(alarmCode.getMachineType())
                .description(alarmCode.getDescription())
                .createdAt(alarmCode.getCreatedAt())
                .build();
    }
}
