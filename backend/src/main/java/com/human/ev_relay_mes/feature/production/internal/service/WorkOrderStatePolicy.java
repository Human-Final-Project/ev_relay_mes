package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.EnumSet;

@Component
@RequiredArgsConstructor
class WorkOrderStatePolicy {

    private static final EnumSet<Lot.Status> NON_TERMINAL_LOT_STATUSES =
            EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);

    private final LotRepository lotRepository;

    void validateTransition(WorkOrder workOrder, WorkOrder.Status targetStatus) {
        WorkOrder.Status currentStatus = workOrder.getStatus();
        if (currentStatus == WorkOrder.Status.CANCELED) {
            throw new CustomException(ErrorCode.WORK_ORDER_CANCELED);
        }
        if (currentStatus == WorkOrder.Status.COMPLETED) {
            throw new CustomException(ErrorCode.WORK_ORDER_ALREADY_COMPLETED);
        }

        boolean allowed = switch (currentStatus) {
            case CREATED -> targetStatus == WorkOrder.Status.RELEASED
                    || targetStatus == WorkOrder.Status.CANCELED;
            case RELEASED -> targetStatus == WorkOrder.Status.CANCELED;
            case RUNNING -> targetStatus == WorkOrder.Status.COMPLETED;
            case COMPLETED, CANCELED -> false;
        };
        if (!allowed) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS);
        }
        if (targetStatus == WorkOrder.Status.CANCELED
                && lotRepository.existsByWorkOrder_WorkOrderId(workOrder.getWorkOrderId())) {
            throw new CustomException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "생산 LOT가 생성된 작업지시는 취소할 수 없습니다.");
        }
        if (targetStatus == WorkOrder.Status.COMPLETED) {
            validateCompletion(workOrder);
        }
    }

    void validateDeletion(WorkOrder workOrder) {
        if (workOrder.getStatus() != WorkOrder.Status.CREATED) {
            throw new CustomException(ErrorCode.WORK_ORDER_ALREADY_STARTED);
        }
        if (lotRepository.existsByWorkOrder_WorkOrderId(workOrder.getWorkOrderId())) {
            throw new CustomException(
                    ErrorCode.RESOURCE_CONFLICT,
                    "연결된 생산 LOT가 있어 삭제할 수 없습니다.");
        }
    }

    private void validateCompletion(WorkOrder workOrder) {
        Long workOrderId = workOrder.getWorkOrderId();
        if (!lotRepository.existsByWorkOrder_WorkOrderId(workOrderId)) {
            throw new CustomException(
                    ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "생산 LOT가 없는 작업지시는 완료할 수 없습니다.");
        }
        boolean hasActiveLot = lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                workOrderId, NON_TERMINAL_LOT_STATUSES);
        long completedOkQty = lotRepository.sumOkQtyByWorkOrderIdAndStatus(
                workOrderId, Lot.Status.COMPLETED);
        if (hasActiveLot || completedOkQty < workOrder.getTargetQty()) {
            throw new CustomException(
                    ErrorCode.WORK_ORDER_TARGET_NOT_MET,
                    "완료 LOT의 누적 양품 수량이 작업지시 목표 수량에 도달해야 완료할 수 있습니다.");
        }
    }
}
