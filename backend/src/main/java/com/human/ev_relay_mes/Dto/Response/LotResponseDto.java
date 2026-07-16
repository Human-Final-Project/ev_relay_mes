package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Member;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LotResponseDto {

    private Long lotId;
    private String lotNo;
    private Long workOrderId;
    private String orderNo;
    private String itemCode;
    private String itemName;
    private String currentProcessCode;
    private String currentProcessName;
    private Integer inputQty;
    private Integer okQty;
    private Integer ngQty;
    private String status;
    private LocalDateTime startedAt;
    private LocalDateTime startRequestedAt;
    private LocalDateTime completedAt;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;

    public static LotResponseDto fromEntity(Lot lot) {
        Member creator = lot.getCreatedBy();
        return LotResponseDto.builder()
                .lotId(lot.getLotId())
                .lotNo(lot.getLotNo())
                .workOrderId(lot.getWorkOrder().getWorkOrderId())
                .orderNo(lot.getWorkOrder().getOrderNo())
                .itemCode(lot.getItem().getItemCode())
                .itemName(lot.getItem().getItemName())
                .currentProcessCode(lot.getCurrentProcess() == null ? null : lot.getCurrentProcess().getProcessCode())
                .currentProcessName(lot.getCurrentProcess() == null ? null : lot.getCurrentProcess().getProcessName())
                .inputQty(lot.getInputQty())
                .okQty(lot.getOkQty())
                .ngQty(lot.getNgQty())
                .status(lot.getStatus().name())
                .startedAt(lot.getStartedAt())
                .startRequestedAt(lot.getStartRequestedAt())
                .completedAt(lot.getCompletedAt())
                .createdById(creator == null ? null : creator.getMemberId())
                .createdByName(creator == null ? null : creator.getMemberName())
                .createdAt(lot.getCreatedAt())
                .build();
    }
}
