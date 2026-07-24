package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ProductionLogResponseDto {

    private Long productionLogId;
    private String lotNo;
    private String machineId;
    private String machineName;
    private String processCode;
    private String processName;
    private Integer inputQty;
    private Integer okQty;
    private Integer ngQty;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private LocalDateTime createdAt;
}
