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
    private String currentLotNo;
    private Integer targetQty;
    private Integer processedQty;
    private Integer progressPercent;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
