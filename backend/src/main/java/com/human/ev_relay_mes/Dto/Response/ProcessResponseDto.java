package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.Process;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ProcessResponseDto {
    private String processCode;
    private String processName;
    private Integer processOrder;
    private String description;
    private String useYn;

    public static ProcessResponseDto fromEntity(Process process) {
        return ProcessResponseDto.builder()
                .processCode(process.getProcessCode())
                .processName(process.getProcessName())
                .processOrder(process.getProcessOrder())
                .description(process.getDescription())
                .useYn(process.getUseYn())
                .build();
    }
}
