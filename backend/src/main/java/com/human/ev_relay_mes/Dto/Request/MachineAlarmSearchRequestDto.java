package com.human.ev_relay_mes.Dto.Request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class MachineAlarmSearchRequestDto {

    private String machineId;
    private String alarmCode;
    private String alarmLevel;
    private Boolean cleared;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
