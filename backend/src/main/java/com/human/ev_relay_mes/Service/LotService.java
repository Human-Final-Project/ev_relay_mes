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
import org.springframework.beans.factory.annotation.Value;
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
    private static final EnumSet<Lot.Status> TERMINAL_STATUSES =
            EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED);

    @Value("${mes.auto-lot.max-supplement-count:3}")
    private int maxSupplementCount = 3;

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
        return createInitialLotAndTryStartProduction(workOrder, memberId);
    }

    /*
     * 최초 LOT를 만든 다음 생산 시작을 시도한다.
     * 자재가 부족하면 LOT은 WAITING 상태로 남고, 자재 입고 후 다시 시도한다.
     */
    @Transactional
    public LotResponseDto createInitialLotAndTryStartProduction(
            WorkOrder workOrder,
            Long memberId) {
        // 1. 확정된 작업지시인지 확인한다.
        if (workOrder.getStatus() != WorkOrder.Status.RELEASED) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "확정(RELEASED) 상태의 작업지시에서만 최초 LOT를 생성할 수 있습니다.");
        }

        // 2. 같은 작업지시에 최초 LOT가 이미 있는지 확인한다.
        if (lotRepository.existsByWorkOrder_WorkOrderId(workOrder.getWorkOrderId())) {
            throw new CustomException(ErrorCode.INITIAL_LOT_ALREADY_EXISTS);
        }

        // 3. 작업지시의 목표 수량으로 최초 LOT를 만들어 저장한다.
        Member creator = findMember(memberId);
        Lot newLot = buildLot(
                workOrder,
                workOrder.getTargetQty(),
                Lot.LotType.INITIAL,
                1,
                creator);
        Lot savedLot = lotRepository.save(newLot);

        // 4. 자재가 준비됐다면 생산을 시작한다.
        tryStartProduction(savedLot);

        return toResponse(savedLot);
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

        return createSupplementLotAndTryStartProduction(
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
        return createSupplementLotAndTryStartProduction(workOrder, remainingQty, creator);
    }

    private LotResponseDto createSupplementLotAndTryStartProduction(
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
        Lot saved = lotRepository.save(buildLot(
                workOrder,
                remainingQty,
                Lot.LotType.SUPPLEMENT,
                nextRound,
                creator));
        tryStartProduction(saved);
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
                tryStartProduction(lot);
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

    private void tryStartProduction(Lot lot) {
        // 자재가 부족하면 요청 시간을 남겨 두고 WAITING 상태를 유지한다.
        lot.setStartRequestedAt(LocalDateTime.now());
        boolean materialsReady = materialLotService.tryConsumeMaterials(lot);
        if (!materialsReady) {
            return;
        }

        // 자재 차감에 성공하면 LOT와 작업지시를 생산 중 상태로 바꾼다.
        if (lot.getStartedAt() == null) {
            lot.setStartedAt(LocalDateTime.now());
        }
        lot.setStartRequestedAt(null);
        lot.setStatus(Lot.Status.RUNNING);
        if (lot.getWorkOrder().getStatus() == WorkOrder.Status.RELEASED) {
            lot.getWorkOrder().setStatus(WorkOrder.Status.RUNNING);
        }

        // 비어 있는 설비에 대기 중인 공정 작업을 배정한다.
        productionScheduleRequestService.requestAllIdleMachines();
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
            tryStartProduction(lot);
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
