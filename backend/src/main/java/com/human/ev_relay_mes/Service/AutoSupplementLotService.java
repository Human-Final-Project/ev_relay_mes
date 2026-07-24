package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;

/** 목표 양품을 채울 때까지 보충 LOT를 자동 생성하는 작업지시 연속 생산 서비스다. */
@Service
@RequiredArgsConstructor
public class AutoSupplementLotService {

    private static final EnumSet<Lot.Status> NON_TERMINAL_STATUSES =
            EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);
    private static final EnumSet<Lot.Status> TERMINAL_STATUSES =
            EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED);

    @Value("${mes.auto-lot.max-supplement-count:3}")
    private int maxSupplementCount = 3;

    private final WorkOrderRepository workOrderRepository;
    private final LotRepository lotRepository;
    private final LotService lotService;

    @Transactional
    public EvaluationResult evaluateAndContinue(Long workOrderId) {
        WorkOrder workOrder = workOrderRepository.findByIdForUpdate(workOrderId)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));

        if (workOrder.getStatus() == WorkOrder.Status.COMPLETED) {
            return EvaluationResult.ALREADY_COMPLETED;
        }
        if (workOrder.getStatus() != WorkOrder.Status.RUNNING) {
            return EvaluationResult.NOT_APPLICABLE;
        }
        if (lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                workOrderId, NON_TERMINAL_STATUSES)) {
            return EvaluationResult.ACTIVE_LOT_EXISTS;
        }
        if (!lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                workOrderId, TERMINAL_STATUSES)) {
            return EvaluationResult.NO_TERMINAL_LOT;
        }

        int completedOkQty = Math.toIntExact(
                lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                        workOrderId, TERMINAL_STATUSES));
        int remainingQty = workOrder.getTargetQty() - completedOkQty;
        if (remainingQty <= 0) {
            workOrder.setStatus(WorkOrder.Status.COMPLETED);
            return EvaluationResult.WORK_ORDER_COMPLETED;
        }

        int maxProductionRound = lotRepository
                .findMaxProductionRoundByWorkOrderId(workOrderId);
        if (maxProductionRound >= 1 + maxSupplementCount) {
            return EvaluationResult.SUPPLEMENT_LIMIT_REACHED;
        }

        lotService.createAutomaticSupplementLot(workOrder, remainingQty);
        return EvaluationResult.SUPPLEMENT_CREATED;
    }

    public enum EvaluationResult {
        WORK_ORDER_COMPLETED,
        SUPPLEMENT_CREATED,
        ACTIVE_LOT_EXISTS,
        NO_TERMINAL_LOT,
        SUPPLEMENT_LIMIT_REACHED,
        ALREADY_COMPLETED,
        NOT_APPLICABLE
    }
}
