package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.WorkOrderRequestDto;
import com.human.ev_relay_mes.Dto.Request.WorkOrderStatusRequestDto;
import com.human.ev_relay_mes.Dto.Response.WorkOrderResponseDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.ItemRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderService {

    private static final EnumSet<Lot.Status> NON_TERMINAL_LOT_STATUSES =
            EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);
    private static final EnumSet<Lot.Status> TERMINAL_LOT_STATUSES =
            EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED);

    @Value("${mes.auto-lot.max-supplement-count:3}")
    private int maxSupplementCount = 3;

    private final WorkOrderRepository workOrderRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final LotRepository lotRepository;
    private final MaterialLotService materialLotService;
    private final LotService lotService;

    @Transactional
    public WorkOrderResponseDto createWorkOrder(WorkOrderRequestDto dto, Long memberId) {
        validatePlan(dto);
        Item item = findUsableItem(dto.getItemCode());
        if (item.getItemType() == Item.ItemType.RM) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "원자재 품목으로는 작업지시를 생성할 수 없습니다.");
        }

        // 작업지시 생성 시점에는 생산 가능 여부만 확인한다.
        // 실제 차감은 LOT 시작 시 다시 확인한 뒤 처리한다.
        materialLotService.validateMaterialAvailability(item.getItemCode(), dto.getTargetQty());

        Member creator = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        WorkOrder workOrder = WorkOrder.builder()
                .orderNo(generateOrderNo())
                .item(item)
                .targetQty(dto.getTargetQty())
                .plannedStartAt(dto.getPlannedStartAt())
                .plannedEndAt(dto.getPlannedEndAt())
                .createdBy(creator)
                .build();
        WorkOrder saved = workOrderRepository.save(workOrder);
        return toResponse(saved);
    }

    public List<WorkOrderResponseDto> getWorkOrders(String status) {
        List<WorkOrder> workOrders = isBlank(status)
                ? workOrderRepository.findAllByOrderByCreatedAtDesc()
                : workOrderRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        return workOrders.stream().map(this::toResponse).toList();
    }

    public WorkOrderResponseDto getWorkOrder(Long id) {
        return toResponse(findWorkOrder(id));
    }

    @Transactional
    public WorkOrderResponseDto updateWorkOrder(Long id, WorkOrderRequestDto dto) {
        validatePlan(dto);
        WorkOrder workOrder = findWorkOrderForUpdate(id);
        if (workOrder.getStatus() != WorkOrder.Status.CREATED) {
            throw new CustomException(ErrorCode.WORK_ORDER_ALREADY_STARTED);
        }
        Item item = findUsableItem(dto.getItemCode());
        if (item.getItemType() == Item.ItemType.RM) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "원자재 품목으로는 작업지시를 생성할 수 없습니다.");
        }

        materialLotService.validateMaterialAvailability(item.getItemCode(), dto.getTargetQty());

        workOrder.setItem(item);
        workOrder.setTargetQty(dto.getTargetQty());
        workOrder.setPlannedStartAt(dto.getPlannedStartAt());
        workOrder.setPlannedEndAt(dto.getPlannedEndAt());
        return toResponse(workOrder);
    }

    /**
     * 작업지시를 확정하고 최초 LOT 생성·자재 차감 시도·파이프라인 투입 요청을
     * 하나의 트랜잭션으로 처리한다.
     */
    @Transactional
    public WorkOrderResponseDto releaseAndStart(Long id, Long memberId) {
        WorkOrder workOrder = findWorkOrderForUpdate(id);
        if (workOrder.getStatus() == WorkOrder.Status.RELEASED) {
            if (!lotRepository.existsByWorkOrder_WorkOrderId(id)) {
                lotService.createInitialLotAndRequestStart(
                        workOrder, resolveLotCreatorId(workOrder, memberId));
            }
            return toResponse(workOrder);
        }
        if (workOrder.getStatus() == WorkOrder.Status.RUNNING) {
            return toResponse(workOrder);
        }
        validateTransition(workOrder, WorkOrder.Status.RELEASED);
        workOrder.setStatus(WorkOrder.Status.RELEASED);
        lotService.createInitialLotAndRequestStart(
                workOrder, resolveLotCreatorId(workOrder, memberId));
        return toResponse(workOrder);
    }

    @Transactional
    public WorkOrderResponseDto updateStatus(Long id, WorkOrderStatusRequestDto dto) {
        WorkOrder workOrder = findWorkOrderForUpdate(id);
        WorkOrder.Status targetStatus = parseStatus(dto.getStatus());
        if (workOrder.getStatus() == targetStatus) {
            if (targetStatus == WorkOrder.Status.RELEASED
                    && !lotRepository.existsByWorkOrder_WorkOrderId(id)) {
                lotService.createInitialLotAndRequestStart(
                        workOrder, resolveLotCreatorId(workOrder, null));
            }
            return toResponse(workOrder);
        }
        if (targetStatus == WorkOrder.Status.RELEASED) {
            validateTransition(workOrder, targetStatus);
            workOrder.setStatus(targetStatus);
            lotService.createInitialLotAndRequestStart(
                    workOrder, resolveLotCreatorId(workOrder, null));
            return toResponse(workOrder);
        }
        validateTransition(workOrder, targetStatus);
        workOrder.setStatus(targetStatus);
        return toResponse(workOrder);
    }

    @Transactional
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = findWorkOrderForUpdate(id);
        if (workOrder.getStatus() != WorkOrder.Status.CREATED) {
            throw new CustomException(ErrorCode.WORK_ORDER_ALREADY_STARTED);
        }
        if (lotRepository.existsByWorkOrder_WorkOrderId(id)) {
            throw new CustomException(ErrorCode.RESOURCE_CONFLICT,
                    "연결된 생산 LOT가 있어 삭제할 수 없습니다.");
        }
        workOrderRepository.delete(workOrder);
    }

    private void validateTransition(WorkOrder workOrder, WorkOrder.Status targetStatus) {
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
            throw new CustomException(ErrorCode.RESOURCE_CONFLICT,
                    "생산 LOT가 생성된 작업지시는 취소할 수 없습니다.");
        }
        if (targetStatus == WorkOrder.Status.COMPLETED) {
            validateCompletion(workOrder);
        }
    }

    private void validateCompletion(WorkOrder workOrder) {
        Long id = workOrder.getWorkOrderId();
        if (!lotRepository.existsByWorkOrder_WorkOrderId(id)) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "생산 LOT가 없는 작업지시는 완료할 수 없습니다.");
        }
        boolean hasActiveLot = lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                id, NON_TERMINAL_LOT_STATUSES);
        long completedOkQty = lotRepository.sumOkQtyByWorkOrderIdAndStatus(
                id, Lot.Status.COMPLETED);
        if (hasActiveLot || completedOkQty < workOrder.getTargetQty()) {
            throw new CustomException(ErrorCode.WORK_ORDER_TARGET_NOT_MET,
                    "완료 LOT의 누적 양품 수량이 작업지시 목표 수량에 도달해야 완료할 수 있습니다.");
        }
    }

    private WorkOrderResponseDto toResponse(WorkOrder workOrder) {
        List<Lot> lots = workOrder.getWorkOrderId() == null
                ? List.of()
                : lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(
                        workOrder.getWorkOrderId());
        if (lots == null) {
            lots = List.of();
        }
        int completedOkQty = lots.stream()
                .filter(lot -> lot.getStatus() == Lot.Status.COMPLETED)
                .mapToInt(Lot::getOkQty)
                .sum();
        int remainingQty = Math.max(workOrder.getTargetQty() - completedOkQty, 0);
        boolean hasTerminalLot = lots.stream()
                .anyMatch(lot -> TERMINAL_LOT_STATUSES.contains(lot.getStatus()));
        boolean hasNonTerminalLot = lots.stream()
                .anyMatch(lot -> NON_TERMINAL_LOT_STATUSES.contains(lot.getStatus()));
        int maxProductionRound = lots.stream()
                .map(Lot::getProductionRound)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        boolean supplementLimitReached = maxProductionRound >= 1 + maxSupplementCount;
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
        int maxProductionRound = lots.stream()
                .map(Lot::getProductionRound)
                .filter(java.util.Objects::nonNull)
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);
        boolean hasTerminalLot = lots.stream()
                .anyMatch(lot -> TERMINAL_LOT_STATUSES.contains(lot.getStatus()));
        if (workOrder.getStatus() == WorkOrder.Status.RUNNING
                && hasTerminalLot
                && remainingQty > 0
                && maxProductionRound >= 1 + maxSupplementCount) {
            return "SUPPLEMENT_LIMIT_REACHED";
        }

        Lot active = lots.stream()
                .filter(lot -> NON_TERMINAL_LOT_STATUSES.contains(lot.getStatus()))
                .findFirst()
                .orElse(null);
        if (active == null) {
            return workOrder.getStatus() == WorkOrder.Status.RELEASED
                    ? "INITIAL_LOT_PENDING"
                    : "AUTO_SUPPLEMENT_PENDING";
        }
        if (active.getStatus() == Lot.Status.HOLD) {
            return "LOT_HOLD";
        }
        boolean supplement = active.getLotType() == Lot.LotType.SUPPLEMENT;
        if (active.getStatus() == Lot.Status.WAITING) {
            return supplement ? "AUTO_SUPPLEMENT_PENDING" : "INITIAL_LOT_PENDING";
        }
        return supplement ? "AUTO_SUPPLEMENT_ACTIVE" : "PIPELINE_ACTIVE";
    }

    private Long resolveLotCreatorId(WorkOrder workOrder, Long requestedMemberId) {
        if (requestedMemberId != null) {
            return requestedMemberId;
        }
        if (workOrder.getCreatedBy() == null) {
            throw new CustomException(ErrorCode.MEMBER_NOT_FOUND,
                    "최초 LOT 생성에 사용할 작업지시 생성자가 없습니다.");
        }
        return workOrder.getCreatedBy().getMemberId();
    }

    private WorkOrder findWorkOrder(Long id) {
        return workOrderRepository.findById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
    }

    private WorkOrder findWorkOrderForUpdate(Long id) {
        return workOrderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new CustomException(ErrorCode.WORK_ORDER_NOT_FOUND));
    }

    private Item findUsableItem(String itemCode) {
        Item item = itemRepository.findById(itemCode)
                .orElseThrow(() -> new CustomException(ErrorCode.ITEM_NOT_FOUND));
        if (!"Y".equalsIgnoreCase(item.getUseYn())) {
            throw new CustomException(ErrorCode.ITEM_NOT_USABLE);
        }
        return item;
    }

    private WorkOrder.Status parseStatus(String status) {
        try {
            return WorkOrder.Status.valueOf(status.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS);
        }
    }

    private void validatePlan(WorkOrderRequestDto dto) {
        if (dto.getPlannedStartAt() != null && dto.getPlannedEndAt() != null
                && dto.getPlannedStartAt().isAfter(dto.getPlannedEndAt())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "계획 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    private String generateOrderNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String orderNo;
        do {
            orderNo = "WO-" + date + "-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase();
        } while (workOrderRepository.existsByOrderNo(orderNo));
        return orderNo;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
