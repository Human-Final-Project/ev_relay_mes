package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class InspectionStandardRequestDto {

    @NotBlank
    private String processCode;

    @NotBlank
    private String inspectionItem;

    @NotBlank
    private String itemName;

    @NotBlank
    private String unit;

    private BigDecimal lowerLimit;
    private BigDecimal upperLimit;

    @Pattern(regexp = "(?i)Y|N")
    private String useYn = "Y";
}
