package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MachineStatusHistoryResponseDto {

    private Long machineStatusHistoryId;
    private String machineId;
    private String machineName;
    private String status;
    private String lotNo;
    private String processCode;
    private String processName;
    private LocalDateTime recordedAt;
    private String message;
}
