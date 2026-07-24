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
    /** 하위 호환용. 자동 보충 구조에서는 보통 즉시 false로 전환된다. */
    private Boolean supplementRequired;
    /** DRAFT, INITIAL_LOT_PENDING, PIPELINE_ACTIVE, LOT_HOLD,
     * AUTO_SUPPLEMENT_PENDING, AUTO_SUPPLEMENT_ACTIVE,
     * SUPPLEMENT_LIMIT_REACHED, COMPLETED, CANCELED */
    private String automationStatus;
    private String status;
    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
    private Long createdById;
    private String createdByName;
    private LocalDateTime createdAt;

    public static WorkOrderResponseDto fromEntity(WorkOrder workOrder) {
        return fromEntity(workOrder, 0, workOrder.getTargetQty(), false, "DRAFT");
    }

    public static WorkOrderResponseDto fromEntity(
            WorkOrder workOrder,
            int completedOkQty,
            int remainingQty,
            boolean supplementRequired,
            String automationStatus) {
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
                .automationStatus(automationStatus)
                .status(workOrder.getStatus().name())
                .plannedStartAt(workOrder.getPlannedStartAt())
                .plannedEndAt(workOrder.getPlannedEndAt())
                .createdById(creator == null ? null : creator.getMemberId())
                .createdByName(creator == null ? null : creator.getMemberName())
                .createdAt(workOrder.getCreatedAt())
                .build();
    }
}
