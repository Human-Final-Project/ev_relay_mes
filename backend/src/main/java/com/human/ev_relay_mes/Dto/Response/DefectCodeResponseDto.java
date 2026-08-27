package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.DefectCode;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DefectCodeResponseDto {

    private String defectCode;
    private String defectName;
    private String processCode;
    private String processName;
    private String description;
    private LocalDateTime createdAt;

    public static DefectCodeResponseDto fromEntity(DefectCode defectCode) {
        return DefectCodeResponseDto.builder()
                .defectCode(defectCode.getDefectCode())
                .defectName(defectCode.getDefectName())
                .processCode(defectCode.getProcess().getProcessCode())
                .processName(defectCode.getProcess().getProcessName())
                .description(defectCode.getDescription())
                .createdAt(defectCode.getCreatedAt())
                .build();
    }
}
