package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.LotMaterialUsage;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LotMaterialUsageResponseDto {

    private Long lotMaterialUsageId;
    private String lotNo;
    private Long materialLotId;
    private String materialLotNo;
    private String itemCode;
    private String itemName;
    private Integer usedQty;
    private LocalDateTime usedAt;

    public static LotMaterialUsageResponseDto fromEntity(LotMaterialUsage usage) {
        return LotMaterialUsageResponseDto.builder()
                .lotMaterialUsageId(usage.getLotMaterialUsageId())
                .lotNo(usage.getLot().getLotNo())
                .materialLotId(usage.getMaterialLot().getMaterialLotId())
                .materialLotNo(usage.getMaterialLot().getMaterialLotNo())
                .itemCode(usage.getMaterialLot().getItem().getItemCode())
                .itemName(usage.getMaterialLot().getItem().getItemName())
                .usedQty(usage.getUsedQty())
                .usedAt(usage.getUsedAt())
                .build();
    }
}
