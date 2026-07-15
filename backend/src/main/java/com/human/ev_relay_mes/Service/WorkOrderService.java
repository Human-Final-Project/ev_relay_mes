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

    private final WorkOrderRepository workOrderRepository;
    private final ItemRepository itemRepository;
    private final MemberRepository memberRepository;
    private final LotRepository lotRepository;

    @Transactional
    public WorkOrderResponseDto createWorkOrder(WorkOrderRequestDto dto, Long memberId) {
        validatePlan(dto);
        Item item = findUsableItem(dto.getItemCode());
        if (item.getItemType() == Item.ItemType.RM) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "원자재 품목으로는 작업지시를 생성할 수 없습니다.");
        }
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
        return WorkOrderResponseDto.fromEntity(workOrderRepository.save(workOrder));
    }

    public List<WorkOrderResponseDto> getWorkOrders(String status) {
        List<WorkOrder> workOrders = isBlank(status)
                ? workOrderRepository.findAllByOrderByCreatedAtDesc()
                : workOrderRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
        return workOrders.stream().map(WorkOrderResponseDto::fromEntity).toList();
    }

    public WorkOrderResponseDto getWorkOrder(Long id) {
        return WorkOrderResponseDto.fromEntity(findWorkOrder(id));
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
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "원자재 품목으로는 작업지시를 생성할 수 없습니다.");
        }
        workOrder.setItem(item);
        workOrder.setTargetQty(dto.getTargetQty());
        workOrder.setPlannedStartAt(dto.getPlannedStartAt());
        workOrder.setPlannedEndAt(dto.getPlannedEndAt());
        return WorkOrderResponseDto.fromEntity(workOrder);
    }

    @Transactional
    public WorkOrderResponseDto updateStatus(Long id, WorkOrderStatusRequestDto dto) {
        WorkOrder workOrder = findWorkOrderForUpdate(id);
        WorkOrder.Status targetStatus = parseStatus(dto.getStatus());
        if (workOrder.getStatus() == targetStatus) {
            return WorkOrderResponseDto.fromEntity(workOrder);
        }
        validateTransition(workOrder, targetStatus);
        workOrder.setStatus(targetStatus);
        return WorkOrderResponseDto.fromEntity(workOrder);
    }

    @Transactional
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = findWorkOrderForUpdate(id);
        if (workOrder.getStatus() != WorkOrder.Status.CREATED) {
            throw new CustomException(ErrorCode.WORK_ORDER_ALREADY_STARTED);
        }
        if (lotRepository.existsByWorkOrder_WorkOrderId(id)) {
            throw new CustomException(ErrorCode.RESOURCE_CONFLICT, "연결된 생산 LOT가 있어 삭제할 수 없습니다.");
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
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS, "생산 LOT가 없는 작업지시는 완료할 수 없습니다.");
        }
        boolean hasActiveLot = lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(id,
                EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD));
        long completedQty = lotRepository.sumInputQtyByWorkOrderIdAndStatus(id, Lot.Status.COMPLETED);
        if (hasActiveLot || completedQty < workOrder.getTargetQty()) {
            throw new CustomException(ErrorCode.INVALID_WORK_ORDER_STATUS,
                    "모든 목표 수량의 생산 LOT가 완료된 후 작업지시를 완료할 수 있습니다.");
        }
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
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "계획 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    private String generateOrderNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String orderNo;
        do {
            orderNo = "WO-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        } while (workOrderRepository.existsByOrderNo(orderNo));
        return orderNo;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
