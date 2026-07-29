package com.human.ev_relay_mes.feature.masterdata.api;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class InspectionStandardRequestDto {
    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;
}
