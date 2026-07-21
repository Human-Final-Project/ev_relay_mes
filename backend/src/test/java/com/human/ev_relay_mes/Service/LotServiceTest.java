package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.LotStatusRequestDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Process;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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


    @Test
    void rejectsStartingNextWorkOrderWhileAnotherWorkOrderIsRunning() {
        Lot waitingLot = createLot();
        WorkOrder runningWorkOrder = WorkOrder.builder()
                .workOrderId(99L)
                .orderNo("WO-RUNNING")
                .item(waitingLot.getItem())
                .targetQty(100)
                .status(WorkOrder.Status.RUNNING)
                .build();
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(waitingLot));
        when(workOrderRepository.findAllForUpdate())
                .thenReturn(List.of(runningWorkOrder, waitingLot.getWorkOrder()));
        LotStatusRequestDto request = new LotStatusRequestDto();
        request.setStatus("RUNNING");

        assertThatThrownBy(() -> lotService.updateStatus(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("목표 양품 수량");

        verify(materialLotService, never()).consumeMaterials(any(), any(Integer.class));
        verify(workCommandService, never()).createInitialStartCommands(any());
    }

    @Test
    void rejectsLaterReleasedWorkOrderUntilEarlierReleasedWorkOrderIsCompleted() {
        Lot laterLot = createLot();
        laterLot.getWorkOrder().setWorkOrderId(2L);
        laterLot.getWorkOrder().setOrderNo("WO-002");
        WorkOrder earlierOrder = WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-001")
                .item(laterLot.getItem())
                .targetQty(10)
                .status(WorkOrder.Status.RELEASED)
                .build();
        when(lotRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(laterLot));
        when(workOrderRepository.findAllForUpdate())
                .thenReturn(List.of(earlierOrder, laterLot.getWorkOrder()));
        LotStatusRequestDto request = new LotStatusRequestDto();
        request.setStatus("RUNNING");

        assertThatThrownBy(() -> lotService.updateStatus(1L, request))
                .isInstanceOf(CustomException.class)
                .hasMessageContaining("먼저 확정된 작업지시");

        verify(materialLotService, never()).consumeMaterials(any(), any(Integer.class));
    }

    @Test
    void createsSupplementLotWithBackendCalculatedRemainingQuantityAndStartsIt() {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .build();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-TEST")
                .item(item)
                .targetQty(100)
                .status(WorkOrder.Status.RUNNING)
                .build();
        Member member = Member.builder().memberId(7L).memberName("관리자").build();
        Process firstProcess = Process.builder()
                .processCode("OP20")
                .processName("코일 권선")
                .processOrder(1)
                .build();

        when(workOrderRepository.findAllForUpdate()).thenReturn(List.of(workOrder));
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatus(1L, Lot.Status.COMPLETED))
                .thenReturn(true);
        when(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(any(), any()))
                .thenReturn(false);
        when(lotRepository.sumOkQtyByWorkOrderIdAndStatus(1L, Lot.Status.COMPLETED))
                .thenReturn(92L);
        when(lotRepository.findMaxProductionRoundByWorkOrderId(1L)).thenReturn(1);
        when(memberRepository.findById(7L)).thenReturn(Optional.of(member));
        when(processRepository.findFirstByOrderByProcessOrderAsc()).thenReturn(Optional.of(firstProcess));
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
        verify(materialLotService).validateMaterialAvailability("FG-001", 8);
        verify(materialLotService).consumeMaterials("FG-001", 8);
        verify(workCommandService).createInitialStartCommands(any(Lot.class));
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
