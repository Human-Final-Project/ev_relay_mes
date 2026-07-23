package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.WorkOrderStatusRequestDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.ItemRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkOrderServiceTest {

    @Mock private WorkOrderRepository workOrderRepository;
    @Mock private ItemRepository itemRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private LotRepository lotRepository;
    @Mock private MaterialLotService materialLotService;
    @Mock private LotService lotService;

    @InjectMocks
    private WorkOrderService workOrderService;

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
}
