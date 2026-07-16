package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.Bom;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class BomResponseDto {
    private Long bomId;
    private String parentItemCode;
    private String childItemCode;
    private BigDecimal quantity;
    private String processCode;
    private String useYn;

    public static BomResponseDto fromEntity(Bom bom){
        return BomResponseDto.builder()
                .bomId(bom.getBomId())
                .parentItemCode(bom.getParentItem().getItemCode())
                .childItemCode(bom.getChildItem().getItemCode())
                .quantity(bom.getQuantity())
                .processCode(bom.getProcess() == null ? null : bom.getProcess().getProcessCode())
                .useYn(bom.getUseYn())
                .build();
    }

}
