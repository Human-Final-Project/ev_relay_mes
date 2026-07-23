package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Entity.WorkCommand;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.InspectionUnitResultRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MachineStatusHistoryRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import com.human.ev_relay_mes.Repository.WorkCommandRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.List;

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
    @Mock WorkCommandRepository workCommandRepository;
    @Mock ProductionLogRepository productionLogRepository;
    @Mock InspectionUnitResultRepository inspectionUnitResultRepository;
    @Mock ProductionScheduleRequestService productionScheduleRequestService;

    @InjectMocks MachineService machineService;

    @Test
    void returnsRunningMachineProgressFromIncrementalProductionLogs() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder").machineType("WINDER")
                .process(process).status(Machine.Status.RUNNING).build();
        Item item = Item.builder().itemCode("FG-001").itemName("relay")
                .itemType(Item.ItemType.FG).build();
        WorkOrder order = WorkOrder.builder().workOrderId(1L).orderNo("WO-001")
                .item(item).targetQty(10).build();
        Lot lot = Lot.builder().lotId(1L).lotNo("LOT-001").workOrder(order).item(item)
                .currentProcess(process).inputQty(10).status(Lot.Status.RUNNING).build();
        WorkCommand command = WorkCommand.builder()
                .commandType(WorkCommand.CommandType.START)
                .machine(machine).process(process).lot(lot).inputQty(10)
                .status(WorkCommand.Status.ACCEPTED).build();

        when(machineRepository.findById("EQ-WIND-01")).thenReturn(Optional.of(machine));
        when(workCommandRepository
                .findFirstByMachine_MachineIdAndStatusInOrderByCreatedAtDescCommandIdDesc(
                        org.mockito.ArgumentMatchers.eq("EQ-WIND-01"), any()))
                .thenReturn(Optional.of(command));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP20"))
                .thenReturn(List.of(
                        ProductionLog.builder().inputQty(4).build(),
                        ProductionLog.builder().inputQty(3).build()));

        var result = machineService.getMachine("EQ-WIND-01");

        assertThat(result.getCurrentLotNo()).isEqualTo("LOT-001");
        assertThat(result.getProcessedQty()).isEqualTo(7);
        assertThat(result.getTargetQty()).isEqualTo(10);
        assertThat(result.getProgressPercent()).isEqualTo(70);
    }

    @Test
    void keepsOriginalTargetQuantityWhileResumeProgressIncreases() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder").machineType("WINDER")
                .process(process).status(Machine.Status.RUNNING).build();
        Lot lot = Lot.builder().lotNo("LOT-001").currentProcess(process)
                .inputQty(10).status(Lot.Status.RUNNING).build();
        WorkCommand start = WorkCommand.builder()
                .commandType(WorkCommand.CommandType.START)
                .machine(machine).process(process).lot(lot).inputQty(10)
                .status(WorkCommand.Status.CANCELED).build();
        WorkCommand resume = WorkCommand.builder()
                .commandType(WorkCommand.CommandType.RESUME)
                .machine(machine).process(process).lot(lot).inputQty(7)
                .status(WorkCommand.Status.ACCEPTED).build();

        when(machineRepository.findById("EQ-WIND-01")).thenReturn(Optional.of(machine));
        when(workCommandRepository
                .findFirstByMachine_MachineIdAndStatusInOrderByCreatedAtDescCommandIdDesc(
                        org.mockito.ArgumentMatchers.eq("EQ-WIND-01"), any()))
                .thenReturn(Optional.of(resume));
        when(workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc("LOT-001"))
                .thenReturn(List.of(start, resume));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP20"))
                .thenReturn(List.of(ProductionLog.builder().inputQty(4).build()));

        var result = machineService.getMachine("EQ-WIND-01");

        assertThat(result.getProcessedQty()).isEqualTo(4);
        assertThat(result.getTargetQty()).isEqualTo(10);
        assertThat(result.getProgressPercent()).isEqualTo(40);
    }

    @Test
    void returnsExistingStatusForDuplicateEventId() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder").machineType("WINDER")
                .process(process).status(Machine.Status.RUNNING).build();
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
    void ignoresDuplicateStatusSnapshotWithDifferentEventId() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder").machineType("WINDER")
                .process(process).status(Machine.Status.ERROR).build();
        MachineStatusHistory latest = MachineStatusHistory.builder()
                .machineStatusHistoryId(21L)
                .eventId("status-old")
                .machine(machine)
                .status(Machine.Status.ERROR)
                .process(process)
                .build();
        MachineStatusReceiveRequestDto dto = new MachineStatusReceiveRequestDto();
        dto.setEventId("status-new");
        dto.setMachineId("EQ-WIND-01");
        dto.setStatus("ERROR");
        dto.setProcessCode("OP20");

        when(machineRepository.findById("EQ-WIND-01")).thenReturn(Optional.of(machine));
        when(processRepository.findById("OP20")).thenReturn(Optional.of(process));
        when(machineStatusHistoryRepository.findFirstByMachine_MachineIdOrderByRecordedAtDesc("EQ-WIND-01"))
                .thenReturn(Optional.of(latest));

        var result = machineService.updateStatus(dto);

        assertThat(result.getMachineStatusHistoryId()).isEqualTo(21L);
        verify(machineStatusHistoryRepository, org.mockito.Mockito.never()).save(any());
    }


    @Test
    void idleStatusRequestsNextPipelineAssignment() {
        Process process = Process.builder()
                .processCode("OP60").processName("sealing").processOrder(60).build();
        Machine machine = Machine.builder()
                .machineId("EQ-SEAL-01").machineName("sealer").machineType("EQ-SEAL")
                .process(process).status(Machine.Status.RUNNING).build();
        MachineStatusReceiveRequestDto dto = new MachineStatusReceiveRequestDto();
        dto.setMachineId("EQ-SEAL-01");
        dto.setStatus("IDLE");
        dto.setProcessCode("OP60");
        dto.setMessage("production_finished");

        when(machineRepository.findById("EQ-SEAL-01")).thenReturn(Optional.of(machine));
        when(processRepository.findById("OP60")).thenReturn(Optional.of(process));
        when(machineStatusHistoryRepository.save(any(MachineStatusHistory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        machineService.updateStatus(dto);

        assertThat(machine.getStatus()).isEqualTo(Machine.Status.IDLE);
        verify(productionScheduleRequestService).requestMachine("EQ-SEAL-01");
    }

    @Test
    void l1RunningReplyCompletesResumeAndRestartsHeldLot() {
        Process process = Process.builder()
                .processCode("OP20").processName("winding").processOrder(20).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WIND-01").machineName("winder").machineType("WINDER")
                .process(process).status(Machine.Status.ERROR).build();
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
