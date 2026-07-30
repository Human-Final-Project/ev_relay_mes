package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.production.api.WorkOrderResponseDto;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
class WorkOrderResponseAssembler {

    private static final EnumSet<Lot.Status> NON_TERMINAL_LOT_STATUSES =
            EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);
    private static final EnumSet<Lot.Status> TERMINAL_LOT_STATUSES =
            EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED);

    private final LotRepository lotRepository;

    @Value("${mes.auto-lot.max-supplement-count:3}")
    private int maxSupplementCount = 3;

    WorkOrderResponseDto toResponse(WorkOrder workOrder) {
        List<Lot> lots = findLots(workOrder);
        int completedOkQty = lots.stream()
                .filter(lot -> lot.getStatus() == Lot.Status.COMPLETED)
                .mapToInt(Lot::getOkQty)
                .sum();
        int remainingQty = Math.max(workOrder.getTargetQty() - completedOkQty, 0);
        boolean hasTerminalLot = hasAnyStatus(lots, TERMINAL_LOT_STATUSES);
        boolean hasNonTerminalLot = hasAnyStatus(lots, NON_TERMINAL_LOT_STATUSES);
        boolean supplementLimitReached = maximumProductionRound(lots)
                >= 1 + maxSupplementCount;
        boolean supplementRequired = workOrder.getStatus() == WorkOrder.Status.RUNNING
                && hasTerminalLot
                && !hasNonTerminalLot
                && remainingQty > 0
                && !supplementLimitReached;

        return WorkOrderResponseDto.fromEntity(
                workOrder,
                completedOkQty,
                remainingQty,
                supplementRequired,
                resolveAutomationStatus(workOrder, lots, remainingQty));
    }

    private List<Lot> findLots(WorkOrder workOrder) {
        if (workOrder.getWorkOrderId() == null) {
            return List.of();
        }
        List<Lot> lots = lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(
                workOrder.getWorkOrderId());
        return lots == null ? List.of() : lots;
    }

    private String resolveAutomationStatus(
            WorkOrder workOrder, List<Lot> lots, int remainingQty) {
        if (workOrder.getStatus() == WorkOrder.Status.CREATED) {
            return "DRAFT";
        }
        if (workOrder.getStatus() == WorkOrder.Status.CANCELED) {
            return "CANCELED";
        }
        if (workOrder.getStatus() == WorkOrder.Status.COMPLETED) {
            return "COMPLETED";
        }
        if (workOrder.getStatus() == WorkOrder.Status.RUNNING
                && hasAnyStatus(lots, TERMINAL_LOT_STATUSES)
                && remainingQty > 0
                && maximumProductionRound(lots) >= 1 + maxSupplementCount) {
            return "SUPPLEMENT_LIMIT_REACHED";
        }

        Lot activeLot = lots.stream()
                .filter(lot -> NON_TERMINAL_LOT_STATUSES.contains(lot.getStatus()))
                .findFirst()
                .orElse(null);
        if (activeLot == null) {
            return workOrder.getStatus() == WorkOrder.Status.RELEASED
                    ? "INITIAL_LOT_PENDING"
                    : "AUTO_SUPPLEMENT_PENDING";
        }
        if (activeLot.getStatus() == Lot.Status.HOLD) {
            return "LOT_HOLD";
        }
        boolean supplement = activeLot.getLotType() == Lot.LotType.SUPPLEMENT;
        if (activeLot.getStatus() == Lot.Status.WAITING) {
            return supplement ? "AUTO_SUPPLEMENT_PENDING" : "INITIAL_LOT_PENDING";
        }
        return supplement ? "AUTO_SUPPLEMENT_ACTIVE" : "PIPELINE_ACTIVE";
    }

    private boolean hasAnyStatus(List<Lot> lots, EnumSet<Lot.Status> statuses) {
        return lots.stream().anyMatch(lot -> statuses.contains(lot.getStatus()));
    }

    private int maximumProductionRound(List<Lot> lots) {
        return lots.stream()
                .map(Lot::getProductionRound)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
    }
}
