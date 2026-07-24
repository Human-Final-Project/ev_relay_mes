package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BomRequestDto {
    @NotBlank
    private String parentItemCode;
    @NotBlank
    private String childItemCode;
    @NotNull
    @DecimalMin(value = "0.0", inclusive = false)
    private BigDecimal quantity;
    @NotBlank
    private String processCode;
}
