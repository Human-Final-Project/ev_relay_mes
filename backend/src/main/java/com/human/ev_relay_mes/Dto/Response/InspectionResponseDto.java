package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class InspectionResponseDto {

    private Long inspectionId;
    private String lotNo;
    private String machineId;
    private String machineName;
    private String processCode;
    private String processName;
    private String inspectionItem;
    private BigDecimal measuredValue;
    private String unit;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
    private String result;
    private LocalDateTime inspectedAt;
}
