package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.LotCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.LotStatusRequestDto;
import com.human.ev_relay_mes.Dto.Response.LotResponseDto;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotService {

    private static final EnumSet<Lot.Status> NON_TERMINAL_STATUSES =
            EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);

    private final LotRepository lotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MemberRepository memberRepository;
    private final ProcessRepository processRepository;
    private final MaterialLotService materialLotService;
    private final ProductionScheduleRequestService productionScheduleRequestService;
    private final LotProcessResponsibleService lotProcessResponsibleService;

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

        Lot saved = lotRepository.save(buildLot(
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
        if (!lotRepository.existsByWorkOrder_WorkOrderIdAndStatus(
                workOrderId, Lot.Status.COMPLETED)) {
            throw new CustomException(ErrorCode.SUPPLEMENT_NOT_REQUIRED,
                    "완료된 최초 LOT가 없어 추가 생산을 생성할 수 없습니다.");
        }
        if (lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                workOrderId, NON_TERMINAL_STATUSES)) {
            throw new CustomException(ErrorCode.SUPPLEMENT_ALREADY_EXISTS);
        }

        int completedOkQty = Math.toIntExact(
                lotRepository.sumOkQtyByWorkOrderIdAndStatus(
                        workOrderId, Lot.Status.COMPLETED));
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
        Lot saved = lotRepository.save(buildLot(
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
            if (!isBlank(status)) {
                Lot.Status parsedStatus = parseStatus(status);
                lots = lots.stream().filter(lot -> lot.getStatus() == parsedStatus).toList();
            }
        } else {
            lots = isBlank(status)
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
        validateTransition(lot, targetStatus);
        if (targetStatus == Lot.Status.RUNNING) {
            validateStartEligibility(lot.getWorkOrder());
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

    private void validateTransition(Lot lot, Lot.Status targetStatus) {
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
            throw new CustomException(ErrorCode.INVALID_LOT_QUANTITY,
                    "투입 수량과 양품·불량 수량이 일치해야 LOT를 완료할 수 있습니다.");
        }
    }

    private void validateStartEligibility(WorkOrder workOrder) {
        if (workOrder.getStatus() != WorkOrder.Status.RELEASED
                && workOrder.getStatus() != WorkOrder.Status.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "확정 또는 생산 중인 작업지시의 LOT만 시작할 수 있습니다.");
        }
    }

    /**
     * 자재는 LOT가 파이프라인에 진입할 때 한 번만 차감한다.
     * 설비가 바쁘면 LOT은 RUNNING 상태로 해당 공정 대기열에 남는다.
     */
    private boolean requestPipelineStart(Lot lot) {
        lot.setStartRequestedAt(LocalDateTime.now());
        boolean consumed = materialLotService.tryConsumeMaterials(
                lot.getItem().getItemCode(), lot.getInputQty());
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
            classes = MaterialLotService.MaterialStockChangedEvent.class,
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
            validateStartEligibility(lot.getWorkOrder());
            requestPipelineStart(lot);
        }
    }

    private Lot buildLot(
            WorkOrder workOrder,
            int inputQty,
            Lot.LotType lotType,
            int productionRound,
            Member creator) {
        Process firstProcess = processRepository.findFirstByOrderByProcessOrderAsc()
                .orElseThrow(() -> new CustomException(ErrorCode.PROCESS_NOT_FOUND));
        return Lot.builder()
                .lotNo(generateLotNo())
                .workOrder(workOrder)
                .item(workOrder.getItem())
                .currentProcess(firstProcess)
                .lotType(lotType)
                .productionRound(productionRound)
                .inputQty(inputQty)
                .createdBy(creator)
                .build();
    }

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
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
        try {
            return Lot.Status.valueOf(status.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_LOT_STATUS);
        }
    }

    private String generateLotNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String lotNo;
        do {
            lotNo = "LOT-" + date + "-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase();
        } while (lotRepository.existsByLotNo(lotNo));
        return lotNo;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LotResponseDto toResponse(Lot lot) {
        return LotResponseDto.fromEntity(
                lot,
                lotProcessResponsibleService.getByLotNo(lot.getLotNo()));
    }
}
