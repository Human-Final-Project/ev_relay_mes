package com.human.ev_relay_mes.Dto.Request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class InspectionSearchRequestDto {

    private String lotNo;
    private String machineId;
    private String processCode;
    private String result;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
