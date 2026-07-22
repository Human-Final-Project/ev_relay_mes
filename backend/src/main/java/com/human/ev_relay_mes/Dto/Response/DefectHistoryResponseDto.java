package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DefectHistoryResponseDto {

    private Long defectHistoryId;
    private String lotNo;
    private String machineId;
    private String machineName;
    private String processCode;
    private String processName;
    private String defectCode;
    private String defectName;
    private String defectDescription;
    private Integer defectQty;
    private LocalDateTime occurredAt;
    private String message;
}
