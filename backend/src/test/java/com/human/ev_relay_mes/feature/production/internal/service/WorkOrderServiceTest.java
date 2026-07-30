package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.production.api.WorkOrderRequestDto;
import com.human.ev_relay_mes.feature.production.api.WorkOrderStatusRequestDto;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.workforce.api.WorkforceAssignmentLookup;
import com.human.ev_relay_mes.feature.production.internal.repository.WorkOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private MasterDataLookup masterDataLookup;
    @Mock private MemberLookup memberLookup;
    @Mock private LotRepository lotRepository;
    @Mock private com.human.ev_relay_mes.feature.material.api.MaterialInventory materialInventory;
    @Mock private LotService lotService;
    @Mock private MachineRegistry machineRegistry;
    @Mock private WorkforceAssignmentLookup workforceAssignmentLookup;

    private WorkOrderService workOrderService;

    @BeforeEach
    void setUp() {
        workOrderService = new WorkOrderService(
                workOrderRepository,
                masterDataLookup,
                memberLookup,
                lotRepository,
                materialInventory,
                lotService,
                new WorkOrderFactory(workOrderRepository),
                new WorkOrderStatePolicy(lotRepository),
                new WorkOrderResponseAssembler(lotRepository),
                machineRegistry,
                workforceAssignmentLookup);
    }

    @Test
    void createsWorkOrderThroughFactoryWithoutChangingCreationRules() {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
        Member creator = Member.builder()
                .memberId(7L)
                .memberName("관리자")
                .build();
        WorkOrderRequestDto request = new WorkOrderRequestDto();
        request.setItemCode("FG-001");
        request.setTargetQty(100);
        when(masterDataLookup.getRequiredItem("FG-001")).thenReturn(item);
        when(memberLookup.getRequiredById(7L)).thenReturn(creator);
        when(workOrderRepository.save(any(WorkOrder.class))).thenAnswer(invocation -> {
            WorkOrder saved = invocation.getArgument(0);
            saved.setWorkOrderId(10L);
            return saved;
        });
        when(lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(10L))
                .thenReturn(List.of());

        var response = workOrderService.createWorkOrder(request, 7L);

        assertThat(response.getOrderNo()).startsWith("WO-");
        assertThat(response.getItemCode()).isEqualTo("FG-001");
        assertThat(response.getTargetQty()).isEqualTo(100);
        assertThat(response.getStatus()).isEqualTo("CREATED");
        assertThat(response.getAutomationStatus()).isEqualTo("DRAFT");
        verify(materialInventory).validateMaterialAvailability("FG-001", 100);
    }

    @Test
    void releasesCreatedWorkOrderAndCreatesInitialLotAutomatically() {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
        Member creator = Member.builder()
                .memberId(7L)
                .memberName("관리자")
                .build();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-TEST")
                .item(item)
                .targetQty(100)
                .createdBy(creator)
                .status(WorkOrder.Status.CREATED)
                .build();
        WorkOrderStatusRequestDto request = new WorkOrderStatusRequestDto();
        request.setStatus("RELEASED");
        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(1L))
                .thenReturn(List.of());

        var response = workOrderService.updateStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo("RELEASED");
        assertThat(workOrder.getStatus()).isEqualTo(WorkOrder.Status.RELEASED);
        verify(lotService).createInitialLotAndRequestStart(workOrder, 7L);
    }

    @Test
    void rejectsReleaseWhenAnyMachineHasNoActiveResponsible() {
        WorkOrder workOrder = workOrder(8L, WorkOrder.Status.CREATED, 100);
        workOrder.setCreatedBy(Member.builder().memberId(7L).build());
        Machine wind = Machine.builder().machineId("EQ-WIND-01").build();
        when(workOrderRepository.findByIdForUpdate(8L)).thenReturn(Optional.of(workOrder));
        when(machineRegistry.getAllMachines()).thenReturn(List.of(wind));
        when(workforceAssignmentLookup.hasActiveResponsible("EQ-WIND-01"))
                .thenReturn(false);

        assertThatThrownBy(() -> workOrderService.releaseAndStart(8L, 7L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.MACHINE_RESPONSIBLE_NOT_ASSIGNED);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrder.Status.CREATED);
    }

    @Test
    void dedicatedReleaseEndpointUsesCurrentOperatorAsLotCreator() {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(2L)
                .orderNo("WO-TEST-2")
                .item(item)
                .targetQty(50)
                .status(WorkOrder.Status.CREATED)
                .build();
        when(workOrderRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(2L))
                .thenReturn(List.of());

        var response = workOrderService.releaseAndStart(2L, 9L);

        assertThat(response.getStatus()).isEqualTo("RELEASED");
        verify(lotService).createInitialLotAndRequestStart(workOrder, 9L);
    }

    @Test
    void completesRunningWorkOrderWhenCompletedLotsMeetTarget() {
        WorkOrder workOrder = workOrder(3L, WorkOrder.Status.RUNNING, 100);
        Lot completedLot = Lot.builder()
                .lotNo("LOT-COMPLETED")
                .workOrder(workOrder)
                .item(workOrder.getItem())
                .inputQty(100)
                .okQty(100)
                .ngQty(0)
                .status(Lot.Status.COMPLETED)
                .build();
        WorkOrderStatusRequestDto request = statusRequest("COMPLETED");
        when(workOrderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.existsByWorkOrder_WorkOrderId(3L)).thenReturn(true);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatus(3L, Lot.Status.COMPLETED))
                .thenReturn(100L);
        when(lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(3L))
                .thenReturn(List.of(completedLot));

        var response = workOrderService.updateStatus(3L, request);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrder.Status.COMPLETED);
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getAutomationStatus()).isEqualTo("COMPLETED");
        assertThat(response.getRemainingQty()).isZero();
    }

    @Test
    void rejectsCompletionWhenCompletedGoodQuantityIsBelowTarget() {
        WorkOrder workOrder = workOrder(4L, WorkOrder.Status.RUNNING, 100);
        WorkOrderStatusRequestDto request = statusRequest("COMPLETED");
        when(workOrderRepository.findByIdForUpdate(4L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.existsByWorkOrder_WorkOrderId(4L)).thenReturn(true);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                org.mockito.ArgumentMatchers.eq(4L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatus(4L, Lot.Status.COMPLETED))
                .thenReturn(99L);

        assertThatThrownBy(() -> workOrderService.updateStatus(4L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.WORK_ORDER_TARGET_NOT_MET);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrder.Status.RUNNING);
    }

    @Test
    void rejectsCancelWhenWorkOrderAlreadyHasLot() {
        WorkOrder workOrder = workOrder(5L, WorkOrder.Status.RELEASED, 50);
        WorkOrderStatusRequestDto request = statusRequest("CANCELED");
        when(workOrderRepository.findByIdForUpdate(5L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.existsByWorkOrder_WorkOrderId(5L)).thenReturn(true);

        assertThatThrownBy(() -> workOrderService.updateStatus(5L, request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RESOURCE_CONFLICT);

        assertThat(workOrder.getStatus()).isEqualTo(WorkOrder.Status.RELEASED);
    }

    @Test
    void reportsAutomaticSupplementPendingAfterTerminalLotFallsShort() {
        WorkOrder workOrder = workOrder(6L, WorkOrder.Status.RUNNING, 100);
        Lot completedLot = Lot.builder()
                .lotNo("LOT-SHORT")
                .workOrder(workOrder)
                .item(workOrder.getItem())
                .lotType(Lot.LotType.INITIAL)
                .productionRound(1)
                .inputQty(100)
                .okQty(80)
                .ngQty(20)
                .status(Lot.Status.COMPLETED)
                .build();
        when(workOrderRepository.findById(6L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(6L))
                .thenReturn(List.of(completedLot));

        var response = workOrderService.getWorkOrder(6L);

        assertThat(response.getCompletedOkQty()).isEqualTo(80);
        assertThat(response.getRemainingQty()).isEqualTo(20);
        assertThat(response.getSupplementRequired()).isTrue();
        assertThat(response.getAutomationStatus()).isEqualTo("AUTO_SUPPLEMENT_PENDING");
    }

    private WorkOrder workOrder(Long id, WorkOrder.Status status, int targetQty) {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
        return WorkOrder.builder()
                .workOrderId(id)
                .orderNo("WO-" + id)
                .item(item)
                .targetQty(targetQty)
                .status(status)
                .build();
    }

    private WorkOrderStatusRequestDto statusRequest(String status) {
        WorkOrderStatusRequestDto request = new WorkOrderStatusRequestDto();
        request.setStatus(status);
        return request;
    }
}
