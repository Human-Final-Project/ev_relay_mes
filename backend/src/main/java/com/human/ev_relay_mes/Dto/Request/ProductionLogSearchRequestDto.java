package com.human.ev_relay_mes.Dto.Request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ProductionLogSearchRequestDto {

    private String lotNo;
    private String processCode;
    private String machineId;
    private String status;
    private LocalDateTime startAt;
    private LocalDateTime endAt;
}
