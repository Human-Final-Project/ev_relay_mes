package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.MaterialLot;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MaterialLotResponseDto {
    private Long materialLotId;
    private String materialLotNo;
    private String itemCode;
    private Integer receivedQty;
    private Integer currentQty;
    private String status;
    private String receivedBy;
    private LocalDateTime receivedAt;
    public static MaterialLotResponseDto fromEntity(MaterialLot lot){
        return MaterialLotResponseDto.builder()
                .materialLotId(lot.getMaterialLotId())
                .materialLotNo(lot.getMaterialLotNo())
                .itemCode(lot.getItem().getItemCode())
                .receivedQty(lot.getReceivedQty())
                .currentQty(lot.getCurrentQty())
                .status(lot.getStatus().name())
                .receivedBy(lot.getReceivedBy().getMemberName())
                .receivedAt(lot.getReceivedAt())
                .build();
    }

}
