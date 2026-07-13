package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MachineResponseDto {

    private String machineId;
    private String machineName;
    private String machineType;
    private String processCode;
    private String processName;
    private String status;
    private String useYn;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
