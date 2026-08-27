package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.EnumSet;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoSupplementLotServiceTest {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private LotRepository lotRepository;
    @Mock private LotService lotService;

    @InjectMocks
    private AutoSupplementLotService service;

    @Test
    void completesWorkOrderWhenAccumulatedOkMeetsTarget() {
        WorkOrder order = order(100);
        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        mockNoActiveLotWithTerminalLot();
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(100L);

        var result = service.evaluateAndContinue(1L);

        assertThat(result).isEqualTo(AutoSupplementLotService.EvaluationResult.WORK_ORDER_COMPLETED);
        assertThat(order.getStatus()).isEqualTo(WorkOrder.Status.COMPLETED);
        verify(lotService, never()).createAutomaticSupplementLot(any(), anyInt());
    }

    @Test
    void createsAutomaticSupplementForRemainingQuantity() {
        WorkOrder order = order(100);
        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        mockNoActiveLotWithTerminalLot();
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(94L);
        when(lotRepository.findMaxProductionRoundByWorkOrderId(1L)).thenReturn(1);

        var result = service.evaluateAndContinue(1L);

        assertThat(result).isEqualTo(AutoSupplementLotService.EvaluationResult.SUPPLEMENT_CREATED);
        verify(lotService).createAutomaticSupplementLot(order, 6);
    }

    @Test
    void doesNotCreateDuplicateSupplementWhileActiveLotExists() {
        WorkOrder order = order(100);
        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(any(), any()))
                .thenReturn(true);

        var result = service.evaluateAndContinue(1L);

        assertThat(result).isEqualTo(AutoSupplementLotService.EvaluationResult.ACTIVE_LOT_EXISTS);
        verify(lotService, never()).createAutomaticSupplementLot(any(), anyInt());
    }

    @Test
    void createsSupplementAfterScrappedLot() {
        WorkOrder order = order(5);
        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        mockNoActiveLotWithTerminalLot();
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(0L);
        when(lotRepository.findMaxProductionRoundByWorkOrderId(1L)).thenReturn(1);

        var result = service.evaluateAndContinue(1L);

        assertThat(result).isEqualTo(AutoSupplementLotService.EvaluationResult.SUPPLEMENT_CREATED);
        verify(lotService).createAutomaticSupplementLot(order, 5);
    }

    @Test
    void stopsAutomaticSupplementAfterThreeSupplementRounds() {
        WorkOrder order = order(5);
        when(workOrderRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(order));
        mockNoActiveLotWithTerminalLot();
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(0L);
        when(lotRepository.findMaxProductionRoundByWorkOrderId(1L)).thenReturn(4);

        var result = service.evaluateAndContinue(1L);

        assertThat(result).isEqualTo(
                AutoSupplementLotService.EvaluationResult.SUPPLEMENT_LIMIT_REACHED);
        verify(lotService, never()).createAutomaticSupplementLot(any(), anyInt());
    }

    private void mockNoActiveLotWithTerminalLot() {
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                eq(1L), eq(nonTerminalStatuses())))
                .thenReturn(false);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                eq(1L), eq(terminalStatuses())))
                .thenReturn(true);
    }

    private EnumSet<Lot.Status> nonTerminalStatuses() {
        return EnumSet.of(Lot.Status.WAITING, Lot.Status.RUNNING, Lot.Status.HOLD);
    }

    private EnumSet<Lot.Status> terminalStatuses() {
        return EnumSet.of(Lot.Status.COMPLETED, Lot.Status.SCRAPPED);
    }

    private WorkOrder order(int targetQty) {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
        return WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-001")
                .item(item)
                .targetQty(targetQty)
                .status(WorkOrder.Status.RUNNING)
                .build();
    }
}
