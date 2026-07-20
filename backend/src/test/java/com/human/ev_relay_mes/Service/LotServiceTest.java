package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.LotStatusRequestDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LotServiceTest {

    @Mock
    private LotRepository lotRepository;
    @Mock
    private WorkOrderRepository workOrderRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private ProcessRepository processRepository;
    @Mock
    private MaterialLotService materialLotService;
    @Mock
    private WorkCommandService workCommandService;
    @Mock
    private LotProcessResponsibleService lotProcessResponsibleService;

    @InjectMocks
    private LotService lotService;

    @Test
    void startsWaitingLotAndWorkOrderTogether() {
        Lot lot = createLot();
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lot));
        LotStatusRequestDto request = new LotStatusRequestDto();
        request.setStatus("RUNNING");

        var response = lotService.updateStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo("RUNNING");
        assertThat(lot.getStartedAt()).isNotNull();
        assertThat(lot.getWorkOrder().getStatus()).isEqualTo(WorkOrder.Status.RUNNING);
        verify(materialLotService).consumeMaterials("FG-001", 10);
        verify(workCommandService).createInitialStartCommands(lot);
    }

    @Test
    void rejectsCompletionWhenProductionQuantityDoesNotMatch() {
        Lot lot = createLot();
        lot.setStatus(Lot.Status.RUNNING);
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lot));
        LotStatusRequestDto request = new LotStatusRequestDto();
        request.setStatus("COMPLETED");

        assertThatThrownBy(() -> lotService.updateStatus(1L, request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void keepsStartRequestedLotWaitingWhenMaterialIsInsufficient() {
        Lot lot = createLot();
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(lot));
        doThrow(new CustomException(com.human.ev_relay_mes.Exception.ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY))
                .when(materialLotService).consumeMaterials("FG-001", 10);
        LotStatusRequestDto request = new LotStatusRequestDto();
        request.setStatus("RUNNING");

        var response = lotService.updateStatus(1L, request);

        assertThat(response.getStatus()).isEqualTo("WAITING");
        assertThat(response.getStartRequestedAt()).isNotNull();
        verify(workCommandService, never()).createInitialStartCommands(lot);
    }

    private Lot createLot() {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-TEST")
                .item(item)
                .targetQty(10)
                .status(WorkOrder.Status.RELEASED)
                .build();
        return Lot.builder()
                .lotId(1L)
                .lotNo("LOT-TEST")
                .workOrder(workOrder)
                .item(item)
                .inputQty(10)
                .okQty(0)
                .ngQty(0)
                .status(Lot.Status.WAITING)
                .build();
    }
}
