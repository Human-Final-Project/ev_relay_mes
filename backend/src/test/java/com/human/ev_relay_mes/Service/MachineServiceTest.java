package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MachineServiceTest {

    @Mock MachineRepository machineRepository;
    @Mock MachineStatusHistoryRepository machineStatusHistoryRepository;
    @Mock ProcessRepository processRepository;
    @Mock LotRepository lotRepository;
    @Mock WorkCommandService workCommandService;

    @InjectMocks MachineService machineService;

    @Test
    void returnsExistingStatusForDuplicateEventId() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder").machineType("WINDER")
                .process(process).status(Machine.Status.RUNNING).useYn("Y").build();
        MachineStatusHistory existing = MachineStatusHistory.builder()
                .machineStatusHistoryId(11L)
                .eventId("status-001")
                .machine(machine)
                .status(Machine.Status.RUNNING)
                .process(process)
                .build();
        MachineStatusReceiveRequestDto dto = new MachineStatusReceiveRequestDto();
        dto.setEventId("status-001");
        dto.setMachineId("EQ-WIND-01");
        dto.setStatus("RUNNING");
        when(machineStatusHistoryRepository.findByEventId("status-001"))
                .thenReturn(Optional.of(existing));

        var result = machineService.updateStatus(dto);

        assertThat(result.getMachineStatusHistoryId()).isEqualTo(11L);
        verifyNoInteractions(machineRepository, processRepository, lotRepository, workCommandService);
    }

    @Test
    void l1RunningReplyCompletesResumeAndRestartsHeldLot() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder").machineType("WINDER")
                .process(process).status(Machine.Status.ERROR).useYn("Y").build();
        Item item = Item.builder().itemCode("FG-001").itemName("relay").itemType(Item.ItemType.FG).build();
        WorkOrder order = WorkOrder.builder().workOrderId(1L).orderNo("WO-001")
                .item(item).targetQty(10).build();
        Lot lot = Lot.builder().lotId(1L).lotNo("LOT-001").workOrder(order).item(item)
                .currentProcess(process).inputQty(10).status(Lot.Status.HOLD).build();
        MachineStatusReceiveRequestDto dto = new MachineStatusReceiveRequestDto();
        dto.setMachineId("EQ-WIND-01");
        dto.setStatus("RUNNING");
        dto.setLotNo("LOT-001");
        dto.setProcessCode("OP20");
        dto.setMessage("production_resumed");

        when(machineRepository.findById("EQ-WIND-01")).thenReturn(Optional.of(machine));
        when(lotRepository.findByLotNo("LOT-001")).thenReturn(Optional.of(lot));
        when(processRepository.findById("OP20")).thenReturn(Optional.of(process));
        when(workCommandService.completeResumeCommand(lot, process, machine)).thenReturn(true);
        when(machineStatusHistoryRepository.save(any(MachineStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = machineService.updateStatus(dto);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.RUNNING);
        assertThat(lot.getStatus()).isEqualTo(Lot.Status.RUNNING);
        assertThat(result.getStatus()).isEqualTo("RUNNING");
        verify(workCommandService).completeResumeCommand(lot, process, machine);
    }
}
