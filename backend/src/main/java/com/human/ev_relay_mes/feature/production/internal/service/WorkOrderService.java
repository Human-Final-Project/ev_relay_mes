package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.common.util.RequestValues;
import com.human.ev_relay_mes.feature.production.api.WorkOrderRequestDto;
import com.human.ev_relay_mes.feature.production.api.WorkOrderStatusRequestDto;
import com.human.ev_relay_mes.feature.production.api.WorkOrderResponseDto;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.production.api.WorkOrderOperations;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.material.api.MaterialInventory;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import com.human.ev_relay_mes.feature.production.internal.repository.WorkOrderRepository;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.workforce.api.WorkforceAssignmentLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkOrderService implements WorkOrderOperations {

    private final WorkOrderRepository workOrderRepository;
    private final MasterDataLookup masterDataLookup;
    private final MemberLookup memberLookup;
    private final LotRepository lotRepository;
    private final MaterialInventory materialInventory;
    private final LotService lotService;
    private final WorkOrderFactory workOrderFactory;
    private final WorkOrderStatePolicy workOrderStatePolicy;
    private final WorkOrderResponseAssembler responseAssembler;
    private final MachineRegistry machineRegistry;
    private final WorkforceAssignmentLookup workforceAssignmentLookup;

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
        materialInventory.validateMaterialAvailability(item.getItemCode(), dto.getTargetQty());

        Member creator = memberLookup.getRequiredById(memberId);
        WorkOrder workOrder = workOrderFactory.create(
                item,
                dto.getTargetQty(),
                dto.getPlannedStartAt(),
                dto.getPlannedEndAt(),
                creator);
        WorkOrder saved = workOrderRepository.save(workOrder);
        return toResponse(saved);
    }

    public List<WorkOrderResponseDto> getWorkOrders(String status) {
        List<WorkOrder> workOrders = RequestValues.isBlank(status)
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

        materialInventory.validateMaterialAvailability(item.getItemCode(), dto.getTargetQty());

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
        workOrderStatePolicy.validateTransition(workOrder, WorkOrder.Status.RELEASED);
        validateResponsibleAssignments();
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
            workOrderStatePolicy.validateTransition(workOrder, targetStatus);
            validateResponsibleAssignments();
            workOrder.setStatus(targetStatus);
            lotService.createInitialLotAndRequestStart(
                    workOrder, resolveLotCreatorId(workOrder, null));
            return toResponse(workOrder);
        }
        workOrderStatePolicy.validateTransition(workOrder, targetStatus);
        workOrder.setStatus(targetStatus);
        return toResponse(workOrder);
    }

    @Transactional
    public void deleteWorkOrder(Long id) {
        WorkOrder workOrder = findWorkOrderForUpdate(id);
        workOrderStatePolicy.validateDeletion(workOrder);
        workOrderRepository.delete(workOrder);
    }

    private void validateResponsibleAssignments() {
        List<String> missingMachineIds = machineRegistry.getAllMachines().stream()
                .map(machine -> machine.getMachineId())
                .filter(machineId -> !workforceAssignmentLookup
                        .hasActiveResponsible(machineId))
                .toList();
        if (!missingMachineIds.isEmpty()) {
            throw new CustomException(
                    ErrorCode.MACHINE_RESPONSIBLE_NOT_ASSIGNED,
                    "책임자 미배정 설비: " + String.join(", ", missingMachineIds));
        }
    }

    private WorkOrderResponseDto toResponse(WorkOrder workOrder) {
        return responseAssembler.toResponse(workOrder);
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
        Item item = masterDataLookup.getRequiredItem(itemCode);
        if (!"Y".equalsIgnoreCase(item.getUseYn())) {
            throw new CustomException(ErrorCode.ITEM_NOT_USABLE);
        }
        return item;
    }

    private WorkOrder.Status parseStatus(String status) {
        return RequestValues.parseEnum(
                WorkOrder.Status.class,
                status,
                ErrorCode.INVALID_WORK_ORDER_STATUS);
    }

    private void validatePlan(WorkOrderRequestDto dto) {
        if (dto.getPlannedStartAt() != null && dto.getPlannedEndAt() != null
                && dto.getPlannedStartAt().isAfter(dto.getPlannedEndAt())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "계획 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

}
