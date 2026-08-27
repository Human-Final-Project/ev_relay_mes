package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InspectionUnitResultResponseDto {
    private String lotNo;
    private String machineId;
    private String processCode;
    private Integer unitSeq;
    private String l1Result;
    private String measurementResult;
    private String result;
    private String evaluationStatus;
}
