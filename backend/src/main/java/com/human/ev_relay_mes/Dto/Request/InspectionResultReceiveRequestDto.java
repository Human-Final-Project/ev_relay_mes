package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class InspectionResultReceiveRequestDto {

    @Size(max = 100)
    private String eventId;

    @NotBlank
    private String lotNo;

    @NotBlank
    private String machineId;

    @NotBlank
    private String processCode;

    @NotNull
    @Positive
    private Integer unitSeq;

    @NotBlank
    private String inspectionItem;

    @NotNull
    private BigDecimal measuredValue;

    private String unit;
}
