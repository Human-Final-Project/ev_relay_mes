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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
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
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(any(), any()))
                .thenReturn(false);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatus(1L, Lot.Status.COMPLETED))
                .thenReturn(true);
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatus(1L, Lot.Status.COMPLETED))
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
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(any(), any()))
                .thenReturn(false);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatus(1L, Lot.Status.COMPLETED))
                .thenReturn(true);
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatus(1L, Lot.Status.COMPLETED))
                .thenReturn(94L);

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
