package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Builder
public class InspectionStandardResponseDto {
    private Long standardId;
    private String processCode;
    private String processName;
    private String inspectionItem;
    private String itemName;
    private String unit;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
    private String useYn;
    private Integer standardVersion;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
