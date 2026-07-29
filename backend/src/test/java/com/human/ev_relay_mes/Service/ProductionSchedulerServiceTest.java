package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.WorkCommandResponseDto;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionSchedulerServiceTest {

    @Mock private LotRepository lotRepository;
    @Mock private MachineRegistry machineRegistry;
    @Mock private MasterDataLookup masterDataLookup;
    @Mock private ProductionLogRepository productionLogRepository;
    @Mock private WorkCommandService workCommandService;
    @Mock private WorkOrderContinuationRequestService workOrderContinuationRequestService;

    @InjectMocks
    private ProductionSchedulerService schedulerService;

    @Test
    void schedulesInitialParallelPairTogether() {
        Lot lot = lot(1L, "LOT-001", process("OP20", 1));
        when(lotRepository.findByLotNoForUpdate("LOT-001")).thenReturn(Optional.of(lot));
        when(workCommandService.tryCreateInitialStartCommands(lot))
                .thenReturn(Optional.of(List.of(
                        org.mockito.Mockito.mock(WorkCommandResponseDto.class),
                        org.mockito.Mockito.mock(WorkCommandResponseDto.class))));

        boolean scheduled = schedulerService.tryScheduleLot("LOT-001");

        assertThat(scheduled).isTrue();
        verify(workCommandService).tryCreateInitialStartCommands(lot);
    }

    @Test
    void assignsOldestReadyLotToIdleSequentialMachine() {
        Process assembly = process("OP40_OP50", 3);
        Process sealing = process("OP60", 4);
        Machine machine = Machine.builder()
                .machineId("EQ-SEAL-01")
                .machineName("sealer")
                .machineType("EQ-SEAL")
                .process(sealing)
                .status(Machine.Status.IDLE)
                .build();
        Lot first = lot(1L, "LOT-001", sealing);
        Lot second = lot(2L, "LOT-002", sealing);
        ProductionLog previous = ProductionLog.builder()
                .lot(first)
                .process(assembly)
                .okQty(8)
                .ngQty(2)
                .inputQty(10)
                .build();

        when(machineRegistry.getRequiredMachine("EQ-SEAL-01")).thenReturn(machine);
        when(lotRepository.findPipelineCandidatesForUpdate(Lot.Status.RUNNING))
                .thenReturn(List.of(first, second));
        when(masterDataLookup.findPreviousProcess(4))
                .thenReturn(Optional.of(assembly));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP40_OP50"))
                .thenReturn(List.of(previous));
        when(workCommandService.tryCreateStartCommand(first, sealing, 8))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(WorkCommandResponseDto.class)));

        boolean assigned = schedulerService.tryAssignMachine("EQ-SEAL-01");

        assertThat(assigned).isTrue();
        verify(workCommandService).tryCreateStartCommand(first, sealing, 8);
        verify(workCommandService, never()).tryCreateStartCommand(
                org.mockito.ArgumentMatchers.eq(second), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void scrapsLegacyZeroInputLotAndContinuesToNextWaitingLot() {
        Process assembly = process("OP40_OP50", 3);
        Process sealing = process("OP60", 4);
        Machine machine = Machine.builder()
                .machineId("EQ-SEAL-01")
                .machineName("sealer")
                .machineType("EQ-SEAL")
                .process(sealing)
                .status(Machine.Status.IDLE)
                .build();
        Lot zeroInputLot = lot(1L, "LOT-ZERO", sealing);
        Lot nextLot = lot(2L, "LOT-NEXT", sealing);
        ProductionLog zeroPrevious = ProductionLog.builder()
                .lot(zeroInputLot).process(assembly).inputQty(10).okQty(0).ngQty(10)
                .status("COMPLETED").build();
        ProductionLog nextPrevious = ProductionLog.builder()
                .lot(nextLot).process(assembly).inputQty(10).okQty(8).ngQty(2)
                .status("COMPLETED").build();

        when(machineRegistry.getRequiredMachine("EQ-SEAL-01")).thenReturn(machine);
        when(lotRepository.findPipelineCandidatesForUpdate(Lot.Status.RUNNING))
                .thenReturn(List.of(zeroInputLot, nextLot));
        when(masterDataLookup.findPreviousProcess(4))
                .thenReturn(Optional.of(assembly));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-ZERO", "OP40_OP50"))
                .thenReturn(List.of(zeroPrevious));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-NEXT", "OP40_OP50"))
                .thenReturn(List.of(nextPrevious));
        when(workCommandService.tryCreateStartCommand(nextLot, sealing, 8))
                .thenReturn(Optional.of(org.mockito.Mockito.mock(WorkCommandResponseDto.class)));

        boolean assigned = schedulerService.tryAssignMachine("EQ-SEAL-01");

        assertThat(assigned).isTrue();
        assertThat(zeroInputLot.getStatus()).isEqualTo(Lot.Status.SCRAPPED);
        assertThat(zeroInputLot.getNgQty()).isEqualTo(10);
        assertThat(zeroInputLot.getCompletedAt()).isNotNull();
        verify(workCommandService).cancelActiveCommandsForLot("LOT-ZERO");
        verify(workOrderContinuationRequestService).requestEvaluation(1L);
        verify(workCommandService).tryCreateStartCommand(nextLot, sealing, 8);
    }

    @Test
    void waitsInsteadOfScrappingWhilePreviousProcessResultIsNotCommitted() {
        Process assembly = process("OP40_OP50", 3);
        Process sealing = process("OP60", 4);
        Machine machine = Machine.builder()
                .machineId("EQ-SEAL-01")
                .process(sealing)
                .status(Machine.Status.IDLE)
                .build();
        Lot lot = lot(1L, "LOT-RACE", sealing);

        when(machineRegistry.getRequiredMachine("EQ-SEAL-01")).thenReturn(machine);
        when(lotRepository.findPipelineCandidatesForUpdate(Lot.Status.RUNNING))
                .thenReturn(List.of(lot));
        when(masterDataLookup.findPreviousProcess(4))
                .thenReturn(Optional.of(assembly));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        "LOT-RACE", "OP40_OP50"))
                .thenReturn(List.of());

        assertThat(schedulerService.tryAssignMachine("EQ-SEAL-01")).isFalse();

        assertThat(lot.getStatus()).isEqualTo(Lot.Status.RUNNING);
        verify(workCommandService, never()).cancelActiveCommandsForLot(any());
        verify(workOrderContinuationRequestService, never()).requestEvaluation(any());
    }

    @Test
    void doesNotAssignHeldLotToAnotherMachine() {
        Process sealing = process("OP60", 4);
        Machine machine = Machine.builder()
                .machineId("EQ-SEAL-01")
                .process(sealing)
                .status(Machine.Status.IDLE)
                .build();
        Lot held = lot(1L, "LOT-HOLD", sealing);
        held.setStatus(Lot.Status.HOLD);

        when(machineRegistry.getRequiredMachine("EQ-SEAL-01")).thenReturn(machine);
        when(lotRepository.findPipelineCandidatesForUpdate(Lot.Status.RUNNING))
                .thenReturn(List.of());

        assertThat(schedulerService.tryAssignMachine("EQ-SEAL-01")).isFalse();
        verify(workCommandService, never()).tryCreateStartCommand(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    private Lot lot(Long id, String lotNo, Process currentProcess) {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("relay")
                .itemType(Item.ItemType.FG)
                .build();
        WorkOrder order = WorkOrder.builder()
                .workOrderId(id)
                .orderNo("WO-" + id)
                .item(item)
                .targetQty(10)
                .status(WorkOrder.Status.RUNNING)
                .build();
        return Lot.builder()
                .lotId(id)
                .lotNo(lotNo)
                .workOrder(order)
                .item(item)
                .currentProcess(currentProcess)
                .inputQty(10)
                .status(Lot.Status.RUNNING)
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
