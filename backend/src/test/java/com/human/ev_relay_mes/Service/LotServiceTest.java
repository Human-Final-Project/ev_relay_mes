package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.LotStatusRequestDto;
import com.human.ev_relay_mes.Entity.Item;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LotServiceTest {

    @Mock private LotRepository lotRepository;
    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private ProcessRepository processRepository;
    @Mock private MaterialLotService materialLotService;
    @Mock private ProductionScheduleRequestService productionScheduleRequestService;
    @Mock private LotProcessResponsibleService lotProcessResponsibleService;

    @InjectMocks
    private LotService lotService;

    @Test
    void createsInitialLotAndStartsPipelineAutomatically() {
        Item item = item();
        Member creator = Member.builder().memberId(7L).memberName("관리자").build();
        WorkOrder order = WorkOrder.builder()
                .workOrderId(1L).orderNo("WO-001").item(item).targetQty(10)
                .status(WorkOrder.Status.RELEASED).createdBy(creator).build();
        Process op20 = process("OP20", 1);
        when(lotRepository.existsByWorkOrder_WorkOrderId(1L)).thenReturn(false);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(creator));
        when(processRepository.findFirstByOrderByProcessOrderAsc()).thenReturn(Optional.of(op20));
        when(materialLotService.tryConsumeMaterials(any(Lot.class))).thenReturn(true);
        when(lotRepository.save(any(Lot.class))).thenAnswer(invocation -> {
            Lot saved = invocation.getArgument(0);
            saved.setLotId(1L);
            return saved;
        });

        var response = lotService.createInitialLotAndRequestStart(order, 7L);

        assertThat(response.getLotType()).isEqualTo("INITIAL");
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(order.getStatus()).isEqualTo(WorkOrder.Status.RUNNING);
        verify(productionScheduleRequestService).requestAllIdleMachines();
    }

    @Test
    void keepsAutomaticallyCreatedInitialLotWaitingForMaterial() {
        Item item = item();
        Member creator = Member.builder().memberId(7L).memberName("관리자").build();
        WorkOrder order = WorkOrder.builder()
                .workOrderId(1L).orderNo("WO-001").item(item).targetQty(10)
                .status(WorkOrder.Status.RELEASED).createdBy(creator).build();
        Process op20 = process("OP20", 1);
        when(lotRepository.existsByWorkOrder_WorkOrderId(1L)).thenReturn(false);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(creator));
        when(processRepository.findFirstByOrderByProcessOrderAsc()).thenReturn(Optional.of(op20));
        when(materialLotService.tryConsumeMaterials(any(Lot.class))).thenReturn(false);
        when(lotRepository.save(any(Lot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = lotService.createInitialLotAndRequestStart(order, 7L);

        assertThat(response.getStatus()).isEqualTo("WAITING");
        assertThat(response.getStartRequestedAt()).isNotNull();
        assertThat(order.getStatus()).isEqualTo(WorkOrder.Status.RELEASED);
        verify(productionScheduleRequestService, never()).requestAllIdleMachines();
    }

    @Test
    void startsWaitingLotAndQueuesItForPipeline() {
        Lot lot = createLot(1L, "LOT-001");
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lot));
        LotStatusRequestDto request = status("RUNNING");
        when(materialLotService.tryConsumeMaterials(any(Lot.class))).thenReturn(true);

        var response = lotService.updateStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(lot.getStartedAt()).isNotNull();
        assertThat(lot.getWorkOrder().getStatus()).isEqualTo(WorkOrder.Status.RUNNING);
        verify(materialLotService).tryConsumeMaterials(any(Lot.class));
        verify(productionScheduleRequestService).requestAllIdleMachines();
    }

    @Test
    void keepsStartRequestedLotWaitingWhenMaterialIsInsufficient() {
        Lot lot = createLot(1L, "LOT-001");
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lot));
        when(materialLotService.tryConsumeMaterials(any(Lot.class))).thenReturn(false);

        var response = lotService.updateStatus(1L, status("RUNNING"));

        assertThat(response.getStatus()).isEqualTo("WAITING");
        assertThat(response.getStartRequestedAt()).isNotNull();
        verify(productionScheduleRequestService, never()).requestAllIdleMachines();
    }

    @Test
    void allowsAnotherWorkOrderLotToEnterPipelineWhileFirstOrderRuns() {
        Lot secondLot = createLot(2L, "LOT-002");
        secondLot.getWorkOrder().setStatus(WorkOrder.Status.RELEASED);
        when(lotRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(secondLot));
        when(materialLotService.tryConsumeMaterials(any(Lot.class))).thenReturn(true);

        var response = lotService.updateStatus(2L, status("RUNNING"));

        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(secondLot.getWorkOrder().getStatus()).isEqualTo(WorkOrder.Status.RUNNING);
        verify(productionScheduleRequestService).requestAllIdleMachines();
    }

    @Test
    void rejectsCompletionWhenProductionQuantityDoesNotMatch() {
        Lot lot = createLot(1L, "LOT-001");
        lot.setStatus(Lot.Status.RUNNING);
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lot));

        assertThatThrownBy(() -> lotService.updateStatus(1L, status("COMPLETED")))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void createsSupplementLotAndQueuesItWithoutGlobalLineLock() {
        Item item = item();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-001")
                .item(item)
                .targetQty(100)
                .status(WorkOrder.Status.RUNNING)
                .build();
        Member member = Member.builder().memberId(7L).memberName("관리자").build();
        Process op20 = process("OP20", 1);

        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(true);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                eq(1L), eq(nonTerminalStatuses())))
                .thenReturn(false);
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(92L);
        when(lotRepository.findMaxProductionRoundByWorkOrderId(1L)).thenReturn(1);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(processRepository.findFirstByOrderByProcessOrderAsc()).thenReturn(Optional.of(op20));
        when(materialLotService.tryConsumeMaterials(any(Lot.class))).thenReturn(true);
        when(lotRepository.save(any(Lot.class))).thenAnswer(invocation -> {
            Lot saved = invocation.getArgument(0);
            saved.setLotId(2L);
            return saved;
        });
        var response = lotService.createSupplementLot(1L, 7L);

        assertThat(response.getLotType()).isEqualTo("SUPPLEMENT");
        assertThat(response.getProductionRound()).isEqualTo(2);
        assertThat(response.getInputQty()).isEqualTo(8);
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        verify(materialLotService).tryConsumeMaterials(any(Lot.class));
        verify(productionScheduleRequestService).requestAllIdleMachines();
    }

    @Test
    void allowsSupplementAfterInitialLotWasScrapped() {
        Item item = item();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-001")
                .item(item)
                .targetQty(3)
                .status(WorkOrder.Status.RUNNING)
                .build();
        Member member = Member.builder().memberId(7L).memberName("관리자").build();
        Process op20 = process("OP20", 1);

        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(workOrder));
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(true);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                eq(1L), eq(nonTerminalStatuses())))
                .thenReturn(false);
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(0L);
        when(lotRepository.findMaxProductionRoundByWorkOrderId(1L)).thenReturn(1);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(processRepository.findFirstByOrderByProcessOrderAsc()).thenReturn(Optional.of(op20));
        when(materialLotService.tryConsumeMaterials(any(Lot.class))).thenReturn(true);
        when(lotRepository.save(any(Lot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = lotService.createSupplementLot(1L, 7L);

        assertThat(response.getProductionRound()).isEqualTo(2);
        assertThat(response.getInputQty()).isEqualTo(3);
        assertThat(response.getStatus()).isEqualTo("RUNNING");
    }

    private EnumSet<Lot.Status> nonTerminalStatuses() {
        return EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);
    }

    private EnumSet<Lot.Status> terminalStatuses() {
        return EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED);
    }

    private LotStatusRequestDto status(String value) {
        LotStatusRequestDto request = new LotStatusRequestDto();
        request.setStatus(value);
        return request;
    }

    private Lot createLot(Long id, String lotNo) {
        Item item = item();
        Process op20 = process("OP20", 1);
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(id)
                .orderNo("WO-" + id)
                .item(item)
                .targetQty(10)
                .status(WorkOrder.Status.RELEASED)
                .build();
        return Lot.builder()
                .lotId(id)
                .lotNo(lotNo)
                .workOrder(workOrder)
                .item(item)
                .currentProcess(op20)
                .inputQty(10)
                .okQty(0)
                .ngQty(0)
                .status(Lot.Status.WAITING)
                .build();
    }

    private Item item() {
        return Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
    }

    private Process process(String code, int order) {
        return Process.builder()
                .processCode(code)
                .processName(code)
                .processOrder(order)
                .build();
    }
}
