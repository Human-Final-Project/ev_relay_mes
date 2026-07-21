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
    private static final EnumSet<Lot.Status> LINE_BLOCKING_STATUSES =
            EnumSet.of(Lot.Status.RUNNING, Lot.Status.HOLD);

    private final LotRepository lotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MemberRepository memberRepository;
    private final ProcessRepository processRepository;
    private final MaterialLotService materialLotService;
    private final WorkCommandService workCommandService;
    private final LotProcessResponsibleService lotProcessResponsibleService;

    /**
     * 작업지시의 최초 생산 LOT를 생성한다.
     * 한 작업지시에는 최초 LOT 하나만 허용하고, 부족 수량은 supplement API로 보충한다.
     */
    @Transactional
    public LotResponseDto createLot(Long workOrderId, LotCreateRequestDto dto, Long memberId) {
        WorkOrder workOrder = findWorkOrderForUpdate(workOrderId);
        if (workOrder.getStatus() != WorkOrder.Status.RELEASED) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "확정(RELEASED) 상태의 작업지시에서만 최초 LOT를 생성할 수 있습니다.");
        }
        if (lotRepository.existsByWorkOrder_WorkOrderId(workOrderId)) {
            throw new CustomException(ErrorCode.INITIAL_LOT_ALREADY_EXISTS);
        }
        if (!workOrder.getTargetQty().equals(dto.getInputQty())) {
            throw new CustomException(ErrorCode.INVALID_LOT_QUANTITY,
                    "최초 LOT 투입 수량은 작업지시 목표 수량과 같아야 합니다.");
        }

        materialLotService.validateMaterialAvailability(
                workOrder.getItem().getItemCode(), dto.getInputQty());

        Lot lot = buildLot(
                workOrder,
                dto.getInputQty(),
                Lot.LotType.INITIAL,
                1,
                findMember(memberId));
        return toResponse(lotRepository.save(lot));
    }

    /**
     * 완료 LOT의 누적 양품 수량을 기준으로 부족 수량을 Backend가 계산하고,
     * 같은 작업지시에 보충 LOT를 생성한 뒤 즉시 생산을 시작한다.
     */
    @Transactional
    public LotResponseDto createSupplementLot(Long workOrderId, Long memberId) {
        // 생산라인 시작 관련 요청은 항상 작업지시 전체를 ID 순서대로 먼저 잠근다.
        // 서로 다른 작업지시에 대한 동시 보충 요청에서도 잠금 순서가 같아 데드락을 줄인다.
        List<WorkOrder> lockedOrders = workOrderRepository.findAllForUpdate();
        WorkOrder workOrder = lockedOrders.stream()
                .filter(order -> order.getWorkOrderId().equals(workOrderId))
                .findFirst()
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
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

        // 보충 LOT 생성과 시작 요청이 동시에 들어와도 하나만 처리되도록
        // 전체 작업지시 행을 동일한 순서로 잠가 생산라인 시작을 직렬화한다.
        validateStartEligibility(workOrder, null, lockedOrders);

        materialLotService.validateMaterialAvailability(
                workOrder.getItem().getItemCode(), remainingQty);

        int nextRound = lotRepository.findMaxProductionRoundByWorkOrderId(workOrderId) + 1;
        Lot supplementLot = buildLot(
                workOrder,
                remainingQty,
                Lot.LotType.SUPPLEMENT,
                nextRound,
                findMember(memberId));
        Lot saved = lotRepository.save(supplementLot);

        // 추가 생산 버튼 한 번으로 LOT 생성과 실제 START를 함께 처리한다.
        startNowOrThrow(saved);
        saved.setStatus(Lot.Status.RUNNING);
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
            return toResponse(lot);
        }
        validateTransition(lot, targetStatus);
        if (targetStatus == Lot.Status.RUNNING) {
            validateStartEligibility(lot.getWorkOrder(), lot.getLotId());
        }
        applyStatus(lot, targetStatus);
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

    private void validateStartEligibility(WorkOrder currentWorkOrder, Long currentLotId) {
        validateStartEligibility(
                currentWorkOrder,
                currentLotId,
                workOrderRepository.findAllForUpdate());
    }

    private void validateStartEligibility(
            WorkOrder currentWorkOrder,
            Long currentLotId,
            List<WorkOrder> lockedOrders) {
        if (currentWorkOrder.getStatus() != WorkOrder.Status.RELEASED
                && currentWorkOrder.getStatus() != WorkOrder.Status.RUNNING) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "확정 또는 생산 중인 작업지시의 LOT만 시작할 수 있습니다.");
        }

        boolean anotherWorkOrderRunning = lockedOrders.stream()
                .anyMatch(order -> !order.getWorkOrderId().equals(currentWorkOrder.getWorkOrderId())
                        && order.getStatus() == WorkOrder.Status.RUNNING);
        if (anotherWorkOrderRunning) {
            throw new CustomException(ErrorCode.ANOTHER_WORK_ORDER_IN_PROGRESS,
                    "현재 생산 중인 작업지시의 목표 양품 수량을 먼저 충족해야 합니다.");
        }

        boolean earlierReleasedOrderExists = lockedOrders.stream()
                .anyMatch(order -> order.getWorkOrderId() < currentWorkOrder.getWorkOrderId()
                        && (order.getStatus() == WorkOrder.Status.RELEASED
                        || order.getStatus() == WorkOrder.Status.RUNNING));
        if (earlierReleasedOrderExists) {
            throw new CustomException(ErrorCode.EARLIER_WORK_ORDER_NOT_COMPLETED,
                    "먼저 확정된 작업지시를 완료한 후 다음 작업지시를 시작할 수 있습니다.");
        }

        long blockingLotCount = currentLotId == null
                ? lotRepository.countLineBlockingLots(
                        LINE_BLOCKING_STATUSES, Lot.Status.WAITING)
                : lotRepository.countAnotherLineBlockingLot(
                        currentLotId, LINE_BLOCKING_STATUSES, Lot.Status.WAITING);
        if (blockingLotCount > 0) {
            throw new CustomException(ErrorCode.ANOTHER_LOT_IN_PROGRESS,
                    "현재 가동 중이거나 시작 대기 중인 LOT가 완료된 후 시작할 수 있습니다.");
        }
    }

    private void applyStatus(Lot lot, Lot.Status targetStatus) {
        if (targetStatus != Lot.Status.RUNNING) {
            lot.setStartRequestedAt(null);
        }
        if (targetStatus == Lot.Status.RUNNING) {
            if (lot.getStartedAt() == null) {
                lot.setStartRequestedAt(LocalDateTime.now());
                if (!tryStart(lot)) {
                    return;
                }
            }
            WorkOrder workOrder = lot.getWorkOrder();
            if (workOrder.getStatus() == WorkOrder.Status.RELEASED) {
                workOrder.setStatus(WorkOrder.Status.RUNNING);
            }
        }
        lot.setStatus(targetStatus);
        if (targetStatus == Lot.Status.COMPLETED || targetStatus == Lot.Status.SCRAPPED) {
            lot.setCompletedAt(LocalDateTime.now());
        }
    }

    private boolean tryStart(Lot lot) {
        try {
            startNowOrThrow(lot);
            return true;
        } catch (CustomException exception) {
            if (exception.getErrorCode() == ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY) {
                return false;
            }
            throw exception;
        }
    }

    private void startNowOrThrow(Lot lot) {
        materialLotService.consumeMaterials(
                lot.getItem().getItemCode(), lot.getInputQty());
        workCommandService.createInitialStartCommands(lot);
        lot.setStartedAt(LocalDateTime.now());
        lot.setStartRequestedAt(null);
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
            try {
                validateStartEligibility(lot.getWorkOrder(), lot.getLotId());
            } catch (CustomException exception) {
                if (exception.getErrorCode() == ErrorCode.ANOTHER_LOT_IN_PROGRESS
                        || exception.getErrorCode() == ErrorCode.ANOTHER_WORK_ORDER_IN_PROGRESS
                        || exception.getErrorCode() == ErrorCode.EARLIER_WORK_ORDER_NOT_COMPLETED) {
                    continue;
                }
                throw exception;
            }
            if (tryStart(lot)) {
                lot.setStatus(Lot.Status.RUNNING);
                if (lot.getWorkOrder().getStatus() == WorkOrder.Status.RELEASED) {
                    lot.getWorkOrder().setStatus(WorkOrder.Status.RUNNING);
                }
                // 전체 생산라인은 한 번에 LOT 하나만 가동한다.
                return;
            }
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
