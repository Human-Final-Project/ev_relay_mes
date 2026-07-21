package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.WorkOrder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkOrderResponseDto {

    private Long workOrderId;
    private String orderNo;
    private String itemCode;
    private String itemName;
    private Integer targetQty;
    private Integer completedOkQty;
    private Integer remainingQty;
    private Boolean supplementRequired;
    private String status;
    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;

    public static WorkOrderResponseDto fromEntity(WorkOrder workOrder) {
        return fromEntity(workOrder, 0, workOrder.getTargetQty(), false);
    }

    public static WorkOrderResponseDto fromEntity(
            WorkOrder workOrder,
            int completedOkQty,
            int remainingQty,
            boolean supplementRequired) {
        Member creator = workOrder.getCreatedBy();
        return WorkOrderResponseDto.builder()
                .workOrderId(workOrder.getWorkOrderId())
                .orderNo(workOrder.getOrderNo())
                .itemCode(workOrder.getItem().getItemCode())
                .itemName(workOrder.getItem().getItemName())
                .targetQty(workOrder.getTargetQty())
                .completedOkQty(completedOkQty)
                .remainingQty(remainingQty)
                .supplementRequired(supplementRequired)
                .status(workOrder.getStatus().name())
                .plannedStartAt(workOrder.getPlannedStartAt())
                .plannedEndAt(workOrder.getPlannedEndAt())
                .createdById(creator == null ? null : creator.getMemberId())
                .createdByName(creator == null ? null : creator.getMemberName())
                .createdAt(workOrder.getCreatedAt())
                .build();
    }
}
