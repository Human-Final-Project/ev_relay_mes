package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import org.springframework.stereotype.Component;

@Component
class LotStatePolicy {

    void validateTransition(Lot lot, Lot.Status targetStatus) {
        boolean allowed = switch (lot.getStatus()) {
            case WAITING -> targetStatus == Lot.Status.RUNNING
                    || targetStatus == Lot.Status.HOLD
                    || targetStatus == Lot.Status.SCRAPPED;
            case RUNNING -> targetStatus == Lot.Status.HOLD
                    || targetStatus == Lot.Status.COMPLETED
                    || targetStatus == Lot.Status.SCRAPPED;
            case HOLD -> targetStatus == Lot.Status.WAITING
                    || targetStatus == Lot.Status.RUNNING
                    || targetStatus == Lot.Status.SCRAPPED;
            case COMPLETED, SCRAPPED -> false;
        };
        if (!allowed) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS_TRANSITION);
        }
        if (targetStatus == Lot.Status.COMPLETED
                && lot.getOkQty() + lot.getNgQty() != lot.getInputQty()) {
            throw new CustomException(
                    ErrorCode.INVALID_LOT_QUANTITY,
                    "투입 수량과 양품·불량 수량이 일치해야 LOT를 완료할 수 있습니다.");
        }
    }

    void validateStartEligibility(WorkOrder workOrder) {
        if (workOrder.getStatus() != WorkOrder.Status.RELEASED
                && workOrder.getStatus() != WorkOrder.Status.RUNNING) {
            throw new CustomException(
                    ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "확정 또는 생산 중인 작업지시의 LOT만 시작할 수 있습니다.");
        }
    }
}
