package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.production.api.LotCreateRequestDto;
import com.human.ev_relay_mes.feature.production.api.LotStatusRequestDto;
import com.human.ev_relay_mes.feature.production.api.LotResponseDto;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.MaterialWaitingLotRetry;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import com.human.ev_relay_mes.feature.material.api.MaterialInventory;
import com.human.ev_relay_mes.feature.material.api.MaterialStockChangedEvent;
import com.human.ev_relay_mes.feature.production.internal.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotService implements MaterialWaitingLotRetry {

    private static final EnumSet<Lot.Status> NON_TERMINAL_STATUSES =
            EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);
    private static final EnumSet<Lot.Status> TERMINAL_STATUSES =
            EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED);

    @Value("${mes.auto-lot.max-supplement-count:3}")
    private int maxSupplementCount = 3;

    private final LotRepository lotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MemberLookup memberLookup;
    private final MaterialInventory materialInventory;
    private final ProductionScheduleRequestService productionScheduleRequestService;
    private final LotProcessResponsibleService lotProcessResponsibleService;
    private final LotFactory lotFactory;
    private final LotStatePolicy lotStatePolicy;

    /**
     * 관리자 복구용 수동 API. 일반 흐름에서는 WorkOrder 확정 시 자동 호출된다.
     * 생성 직후 자재를 확인하고 파이프라인 투입까지 요청한다.
     */
    @Transactional
    public LotResponseDto createLot(Long workOrderId, LotCreateRequestDto dto, Long memberId) {
        WorkOrder workOrder = findWorkOrderForUpdate(workOrderId);
        if (!workOrder.getTargetQty().equals(dto.getInputQty())) {
            throw new CustomException(ErrorCode.INVALID_LOT_QUANTITY,
                    "최초 LOT 투입 수량은 작업지시 목표 수량과 같아야 합니다.");
        }
        return createInitialLotAndRequestStart(workOrder, memberId);
    }

    /** WorkOrder 확정 트랜잭션 안에서 최초 LOT를 원자적으로 생성하고 자동 투입한다. */
    @Transactional
    public LotResponseDto createInitialLotAndRequestStart(WorkOrder workOrder, Long memberId) {
        if (workOrder.getStatus() != WorkOrder.Status.RELEASED) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "확정(RELEASED) 상태의 작업지시에서만 최초 LOT를 생성할 수 있습니다.");
        }
        if (lotRepository.existsByWorkOrder_WorkOrderId(workOrder.getWorkOrderId())) {
            throw new CustomException(ErrorCode.INITIAL_LOT_ALREADY_EXISTS);
        }

        Lot saved = lotRepository.save(lotFactory.create(
                workOrder,
                workOrder.getTargetQty(),
                Lot.LotType.INITIAL,
                1,
                findMember(memberId)));
        requestPipelineStart(saved);
        return toResponse(saved);
    }

    /**
     * 완료 LOT의 누적 양품을 기준으로 부족 수량을 계산하여 보충 LOT를 만든다.
     * 다른 WorkOrder가 실행 중이어도 해당 LOT는 파이프라인 대기열에 들어갈 수 있다.
     */
    @Transactional
    public LotResponseDto createSupplementLot(Long workOrderId, Long memberId) {
        WorkOrder workOrder = findWorkOrderForUpdate(workOrderId);
        if (workOrder.getStatus() != WorkOrder.Status.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "생산 중이며 목표 수량이 부족한 작업지시만 추가 생산할 수 있습니다.");
        }
        if (!lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                workOrderId, TERMINAL_STATUSES)) {
            throw new CustomException(ErrorCode.SUPPLEMENT_NOT_REQUIRED,
                    "종료된 최초 LOT가 없어 추가 생산을 생성할 수 없습니다.");
        }
        if (lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                workOrderId, NON_TERMINAL_STATUSES)) {
            throw new CustomException(ErrorCode.SUPPLEMENT_ALREADY_EXISTS);
        }

        int completedOkQty = Math.toIntExact(
                lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                        workOrderId, TERMINAL_STATUSES));
        int remainingQty = workOrder.getTargetQty() - completedOkQty;
        if (remainingQty <= 0) {
            throw new CustomException(ErrorCode.SUPPLEMENT_NOT_REQUIRED);
        }

        return createSupplementLotAndRequestStart(
                workOrder,
                remainingQty,
                findMember(memberId));
    }


    /**
     * 최종 양품 부족분을 자동 보충 LOT로 생성한다.
     * 자재가 부족해도 LOT은 WAITING/startRequestedAt 상태로 남아 입고 이벤트 때 자동 재시도된다.
     */
    @Transactional
    public LotResponseDto createAutomaticSupplementLot(WorkOrder workOrder, int remainingQty) {
        Member creator = workOrder.getCreatedBy();
        if (creator == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND,
                    "자동 보충 LOT 생성에 사용할 작업지시 생성자가 없습니다.");
        }
        return createSupplementLotAndRequestStart(workOrder, remainingQty, creator);
    }

    private LotResponseDto createSupplementLotAndRequestStart(
            WorkOrder workOrder,
            int remainingQty,
            Member creator) {
        if (remainingQty <= 0) {
            throw new CustomException(ErrorCode.SUPPLEMENT_NOT_REQUIRED);
        }
        int nextRound = lotRepository
                .findMaxProductionRoundByWorkOrderId(workOrder.getWorkOrderId()) + 1;
        if (nextRound > 1 + maxSupplementCount) {
            throw new CustomException(ErrorCode.SUPPLEMENT_LIMIT_REACHED,
                    "자동 보충 LOT는 최대 " + maxSupplementCount + "회까지 생성할 수 있습니다.");
        }
        Lot saved = lotRepository.save(lotFactory.create(
                workOrder,
                remainingQty,
                Lot.LotType.SUPPLEMENT,
                nextRound,
                creator));
        requestPipelineStart(saved);
        return toResponse(saved);
    }

    public List<LotResponseDto> getLots(String status, Long workOrderId) {
        List<Lot> lots;
        if (workOrderId != null) {
            findWorkOrder(workOrderId);
            lots = lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(workOrderId);
            if (!RequestValues.isBlank(status)) {
                Lot.Status parsedStatus = parseStatus(status);
                lots = lots.stream().filter(lot -> lot.getStatus() == parsedStatus).toList();
            }
        } else {
            lots = RequestValues.isBlank(status)
                    ? lotRepository.findAllByOrderByCreatedAtDesc()
                    : lotRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        }
        return lots.stream().map(this::toResponse).toList();
    }

    public LotResponseDto getLot(Long id) {
        return toResponse(findLot(id));
    }

    public LotResponseDto getLotByNo(String lotNo) {
        return toResponse(findLotByNo(lotNo));
    }

    @Transactional
    public LotResponseDto updateStatus(Long id, LotStatusRequestDto dto) {
        Lot lot = findLotForUpdate(id);
        Lot.Status targetStatus = parseStatus(dto.getStatus());
        if (lot.getStatus() == targetStatus) {
            if (targetStatus == Lot.Status.RUNNING) {
                productionScheduleRequestService.requestLot(lot.getLotNo());
            }
            return toResponse(lot);
        }
        lotStatePolicy.validateTransition(lot, targetStatus);
        if (targetStatus == Lot.Status.RUNNING) {
            lotStatePolicy.validateStartEligibility(lot.getWorkOrder());
            if (lot.getStatus() == Lot.Status.HOLD) {
                lot.setStatus(Lot.Status.RUNNING);
                productionScheduleRequestService.requestLot(lot.getLotNo());
            } else {
                requestPipelineStart(lot);
            }
            return toResponse(lot);
        }

        lot.setStartRequestedAt(null);
        lot.setStatus(targetStatus);
        if (targetStatus == Lot.Status.COMPLETED || targetStatus == Lot.Status.SCRAPPED) {
            lot.setCompletedAt(LocalDateTime.now());
        }
        return toResponse(lot);
    }

    @Transactional
    public void deleteLot(Long id) {
        Lot lot = findLotForUpdate(id);
        if (lot.getStatus() != Lot.Status.WAITING) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS_TRANSITION,
                    "대기 상태의 LOT만 삭제할 수 있습니다.");
        }
        lotRepository.delete(lot);
    }

    /**
     * 자재는 LOT가 파이프라인에 진입할 때 한 번만 차감한다.
     * 설비가 바쁘면 LOT은 RUNNING 상태로 해당 공정 대기열에 남는다.
     */
    private boolean requestPipelineStart(Lot lot) {
        lot.setStartRequestedAt(LocalDateTime.now());
        boolean consumed = materialInventory.tryConsumeMaterials(lot);
        if (!consumed) {
            return false;
        }

        if (lot.getStartedAt() == null) {
            lot.setStartedAt(LocalDateTime.now());
        }
        lot.setStartRequestedAt(null);
        lot.setStatus(Lot.Status.RUNNING);
        if (lot.getWorkOrder().getStatus() == WorkOrder.Status.RELEASED) {
            lot.getWorkOrder().setStatus(WorkOrder.Status.RUNNING);
        }
        // 새 LOT을 직접 우선 배정하지 않고 전체 IDLE 설비를 FIFO 대기열 기준으로 채운다.
        productionScheduleRequestService.requestAllIdleMachines();
        return true;
    }

    @TransactionalEventListener(
            classes = MaterialStockChangedEvent.class,
            phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void retryMaterialWaitingLots() {
        List<Long> lotIds = lotRepository
                .findByStatusAndStartRequestedAtIsNotNullOrderByStartRequestedAtAsc(Lot.Status.WAITING)
                .stream().map(Lot::getLotId).toList();
        for (Long lotId : lotIds) {
            Lot lot = findLotForUpdate(lotId);
            if (lot.getStatus() != Lot.Status.WAITING || lot.getStartRequestedAt() == null) {
                continue;
            }
            lotStatePolicy.validateStartEligibility(lot.getWorkOrder());
            requestPipelineStart(lot);
        }
    }

    private Member findMember(Long memberId) {
        return memberLookup.getRequiredById(memberId);
    }

    private Lot findLot(Long id) {
        return lotRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private Lot findLotForUpdate(Long id) {
        return lotRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private Lot findLotByNo(String lotNo) {
        return lotRepository.findByLotNo(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
    }

    private WorkOrder findWorkOrderForUpdate(Long id) {
        return workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
    }

    private Lot.Status parseStatus(String status) {
        return RequestValues.parseEnum(
                Lot.Status.class, status, ErrorCode.INVALID_LOT_STATUS);
    }

    private LotResponseDto toResponse(Lot lot) {
        return LotResponseDto.fromEntity(
                lot,
                lotProcessResponsibleService.getByLotNo(lot.getLotNo()));
    }
}
