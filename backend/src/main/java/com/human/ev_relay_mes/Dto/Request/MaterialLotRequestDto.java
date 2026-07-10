package com.human.ev_relay_mes.Dto.Request;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaterialLotRequestDto {
    private String materialLotNo;
    private String itemCode;
    private Integer receivedQty;
    private Long receivedBy;
}
