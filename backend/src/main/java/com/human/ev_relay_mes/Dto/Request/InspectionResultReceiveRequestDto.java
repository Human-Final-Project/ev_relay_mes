package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class InspectionResultReceiveRequestDto {

    @NotBlank
    private String lotNo;

    @NotBlank
    private String machineId;

    @NotBlank
    private String processCode;

    @NotBlank
    private String inspectionItem;

    private BigDecimal measuredValue;
    private String unit;
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;

    @NotNull
    private String result;
}
