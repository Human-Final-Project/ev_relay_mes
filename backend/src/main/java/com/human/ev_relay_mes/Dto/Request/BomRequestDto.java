package com.human.ev_relay_mes.Dto.Request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class BomRequestDto {
    private String parentItemCode;
    private String childItemCode;
    private BigDecimal quantity;
    private String processCode;
}
