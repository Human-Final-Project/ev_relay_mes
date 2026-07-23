package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MachineAlarmResponseDto {

    private Long machineAlarmHistoryId;
    private String machineId;
    private String machineName;
    private String alarmCode;
    private String alarmName;
    private String description;
    private String alarmLevel;
    private String lotNo;
    private String processCode;
    private String processName;
    private LocalDateTime occurredAt;
    private LocalDateTime clearedAt;
    private Long clearedById;
    private String clearedByName;
    private String message;
    private Boolean cleared;
}
