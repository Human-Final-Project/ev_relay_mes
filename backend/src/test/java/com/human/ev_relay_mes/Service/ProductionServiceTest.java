package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import com.human.ev_relay_mes.Repository.ProductionLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.ArrayList;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ProductionServiceTest {

    @Mock
    private ProductionLogRepository productionLogRepository;
    @Mock
    private MachineRepository machineRepository;
    @Mock
    private ProcessRepository processRepository;
    @Mock
    private LotRepository lotRepository;
    @Mock
    private WorkCommandService workCommandService;
    @Mock
    private ProductionScheduleRequestService productionScheduleRequestService;
    @Mock
    private WorkOrderContinuationRequestService workOrderContinuationRequestService;

    @InjectMocks
    private ProductionService productionService;

    @Test
    void returnsExistingProductionResultForDuplicateEventId() {
        Fixture fixture = fixture();
        ProductionLog existing = ProductionLog.builder()
                .productionLogId(10L)
                .eventId("production-001")
                .lot(fixture.lot)
                .machine(fixture.machine)
                .process(fixture.process)
                .inputQty(10)
                .okQty(9)
                .ngQty(1)
                .status("COMPLETED")
                .build();
        ProductionResultReceiveRequestDto request = request(10, 9, 1, "COMPLETED");
        request.setEventId(" production-001 ");
        when(lotRepository.findByLotNoForUpdate("LOT-001"))
                .thenReturn(Optional.of(fixture.lot));
        when(productionLogRepository.findByEventId("production-001"))
                .thenReturn(Optional.of(existing));

        var response = productionService.saveResult(request);

        assertThat(response.getProductionLogId()).isEqualTo(10L);
        var ordered = inOrder(lotRepository, productionLogRepository);
        ordered.verify(lotRepository).findByLotNoForUpdate("LOT-001");
        ordered.verify(productionLogRepository).findByEventId("production-001");
        verifyNoInteractions(machineRepository, processRepository, workCommandService);
    }

    @Test
    void completesFinalProcessLotAndRequestsAutomaticWorkOrderEvaluation() {
        Fixture fixture = fixture();
        ProductionResultReceiveRequestDto request = request(10, 10, 0, "COMPLETED");
        mockBase(fixture);
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP10"))
                .thenReturn(List.of());
        when(productionLogRepository.save(any(ProductionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(processRepository.findFirstByProcessOrderGreaterThanOrderByProcessOrderAsc(1))
                .thenReturn(Optional.empty());
        var response = productionService.saveResult(request);

        assertThat(response.getStatus()).isEqualTo("COMPLETED");
        assertThat(response.getEndedAt()).isNotNull();
        assertThat(fixture.lot.getStatus()).isEqualTo(Lot.Status.COMPLETED);
        assertThat(fixture.lot.getCompletedAt()).isEqualTo(response.getEndedAt());
        assertThat(fixture.lot.getOkQty()).isEqualTo(10);
        assertThat(fixture.lot.getNgQty()).isZero();
        assertThat(fixture.workOrder.getStatus()).isEqualTo(WorkOrder.Status.RUNNING);
        verify(workOrderContinuationRequestService).requestEvaluation(1L);
    }

    @Test
    void includesEarlierProcessLossInFinalLotNgQuantity() {
        Item item = Item.builder()
                .itemCode("FG-001").itemName("EV Relay").itemType(Item.ItemType.FG).build();
        Process inspection = Process.builder()
                .processCode("OP70").processName("Inspection").processOrder(5).build();
        Process packing = Process.builder()
                .processCode("OP80").processName("Packing").processOrder(6).build();
        Machine machine = Machine.builder()
                .machineId("EQ-PACK-01").machineName("Packer").machineType("PACK")
                .process(packing).status(Machine.Status.RUNNING).build();
        WorkOrder order = WorkOrder.builder()
                .workOrderId(1L).orderNo("WO-001").item(item).targetQty(10)
                .status(WorkOrder.Status.RUNNING).build();
        Lot lot = Lot.builder()
                .lotId(1L).lotNo("LOT-001").workOrder(order).item(item)
                .currentProcess(packing).inputQty(10).okQty(0).ngQty(0)
                .status(Lot.Status.RUNNING).build();
        ProductionLog inspectionResult = ProductionLog.builder()
                .lot(lot).machine(machine).process(inspection)
                .inputQty(10).okQty(5).ngQty(5).status("COMPLETED").build();
        ProductionResultReceiveRequestDto request = request(5, 5, 0, "COMPLETED");
        request.setMachineId("EQ-PACK-01");
        request.setProcessCode("OP80");

        when(lotRepository.findByLotNoForUpdate("LOT-001")).thenReturn(Optional.of(lot));
        when(machineRepository.findById("EQ-PACK-01")).thenReturn(Optional.of(machine));
        when(processRepository.findById("OP80")).thenReturn(Optional.of(packing));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP80"))
                .thenReturn(List.of());
        when(processRepository.findFirstByProcessOrderLessThanOrderByProcessOrderDesc(6))
                .thenReturn(Optional.of(inspection));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP70"))
                .thenReturn(List.of(inspectionResult));
        when(productionLogRepository.save(any(ProductionLog.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(processRepository.findFirstByProcessOrderGreaterThanOrderByProcessOrderAsc(6))
                .thenReturn(Optional.empty());
        productionService.saveResult(request);

        assertThat(lot.getOkQty()).isEqualTo(5);
        assertThat(lot.getNgQty()).isEqualTo(5);
        assertThat(lot.getStatus()).isEqualTo(Lot.Status.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(WorkOrder.Status.RUNNING);
        verify(workOrderContinuationRequestService).requestEvaluation(1L);
    }

    @Test
    void rejectsQuantityOverLotInput() {
        Fixture fixture = fixture();
        ProductionResultReceiveRequestDto request = request(11, 11, 0, "RUNNING");
        mockBase(fixture);
        ProductionLog previous = ProductionLog.builder()
                .lot(fixture.lot)
                .machine(fixture.machine)
                .process(fixture.process)
                .inputQty(6)
                .okQty(6)
                .ngQty(0)
                .build();
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP10"))
                .thenReturn(List.of(previous));

        assertThatThrownBy(() -> productionService.saveResult(request))
                .isInstanceOf(CustomException.class);
    }

    @Test
    void updatesExistingProcessLogWithLatestCumulativeQuantity() {
        Fixture fixture = fixture();
        ProductionLog current = ProductionLog.builder()
                .productionLogId(20L)
                .lot(fixture.lot)
                .machine(fixture.machine)
                .process(fixture.process)
                .inputQty(4)
                .okQty(4)
                .ngQty(0)
                .status("RUNNING")
                .build();
        ProductionResultReceiveRequestDto request = request(7, 6, 1, "RUNNING");
        mockBase(fixture);
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP10"))
                .thenReturn(List.of(current));
        when(productionLogRepository.save(current)).thenReturn(current);

        var response = productionService.saveResult(request);

        assertThat(response.getProductionLogId()).isEqualTo(20L);
        assertThat(response.getInputQty()).isEqualTo(7);
        assertThat(response.getOkQty()).isEqualTo(6);
        assertThat(response.getNgQty()).isEqualTo(1);
        assertThat(response.getStatus()).isEqualTo("RUNNING");
        verify(productionLogRepository).save(current);
    }

    @Test
    void ignoresOlderCumulativeProgressWithoutRegressingTheLog() {
        Fixture fixture = fixture();
        ProductionLog current = ProductionLog.builder()
                .productionLogId(21L)
                .lot(fixture.lot)
                .machine(fixture.machine)
                .process(fixture.process)
                .inputQty(6)
                .okQty(6)
                .ngQty(0)
                .status("RUNNING")
                .build();
        ProductionResultReceiveRequestDto request = request(5, 5, 0, "RUNNING");
        when(lotRepository.findByLotNoForUpdate("LOT-001"))
                .thenReturn(Optional.of(fixture.lot));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP10"))
                .thenReturn(List.of(current));

        var response = productionService.saveResult(request);

        assertThat(response.getInputQty()).isEqualTo(6);
        verifyNoInteractions(machineRepository, processRepository, workCommandService);
    }

    @Test
    void advancesToAssemblyOnlyAfterBothParallelProcessesComplete() {
        Process op20 = Process.builder()
                .processCode("OP20").processName("Winding").processOrder(1).build();
        Process op30 = Process.builder()
                .processCode("OP30").processName("Welding").processOrder(2).build();
        Process assembly = Process.builder()
                .processCode("OP40_OP50").processName("Assembly").processOrder(3).build();
        Machine machine = Machine.builder()
                .machineId("EQ-WELD-01").machineName("Welder").machineType("WELD")
                .process(op30).status(Machine.Status.RUNNING).build();
        WorkOrder order = WorkOrder.builder()
                .workOrderId(1L).orderNo("WO-001").targetQty(10)
                .status(WorkOrder.Status.RUNNING).build();
        Lot lot = Lot.builder()
                .lotNo("LOT-001").workOrder(order).currentProcess(op20)
                .inputQty(10).okQty(0).ngQty(0).status(Lot.Status.RUNNING).build();
        ProductionLog op20Result = ProductionLog.builder()
                .lot(lot).process(op20).machine(machine)
                .inputQty(10).okQty(9).ngQty(1).status("COMPLETED").build();
        List<ProductionLog> op30Results = new ArrayList<>();

        when(lotRepository.findByLotNoForUpdate("LOT-001")).thenReturn(Optional.of(lot));
        when(machineRepository.findById("EQ-WELD-01")).thenReturn(Optional.of(machine));
        when(processRepository.findById("OP30")).thenReturn(Optional.of(op30));
        when(processRepository.findById("OP40_OP50")).thenReturn(Optional.of(assembly));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP20"))
                .thenReturn(List.of(op20Result));
        when(productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc("LOT-001", "OP30"))
                .thenAnswer(invocation -> List.copyOf(op30Results));
        when(productionLogRepository.save(any(ProductionLog.class))).thenAnswer(invocation -> {
            ProductionLog saved = invocation.getArgument(0);
            op30Results.add(saved);
            return saved;
        });

        ProductionResultReceiveRequestDto request = request(10, 8, 2, "COMPLETED");
        request.setMachineId("EQ-WELD-01");
        request.setProcessCode("OP30");

        productionService.saveResult(request);

        assertThat(lot.getCurrentProcess()).isEqualTo(assembly);
        verify(productionScheduleRequestService).requestMachine("EQ-WELD-01");
        verify(productionScheduleRequestService).requestLot("LOT-001");
    }

    private void mockBase(Fixture fixture) {
        when(lotRepository.findByLotNoForUpdate("LOT-001")).thenReturn(Optional.of(fixture.lot));
        when(machineRepository.findById("MC-001")).thenReturn(Optional.of(fixture.machine));
        when(processRepository.findById("OP10")).thenReturn(Optional.of(fixture.process));
    }

    private ProductionResultReceiveRequestDto request(int input, int ok, int ng, String status) {
        ProductionResultReceiveRequestDto request = new ProductionResultReceiveRequestDto();
        request.setLotNo("LOT-001");
        request.setMachineId("MC-001");
        request.setProcessCode("OP10");
        request.setInputQty(input);
        request.setOkQty(ok);
        request.setNgQty(ng);
        request.setStatus(status);
        return request;
    }

    private Fixture fixture() {
        Item item = Item.builder()
                .itemCode("FG-001")
                .itemName("EV Relay")
                .itemType(Item.ItemType.FG)
                .useYn("Y")
                .build();
        Process process = Process.builder()
                .processCode("OP10")
                .processName("Assembly")
                .processOrder(1)
                .build();
        Machine machine = Machine.builder()
                .machineId("MC-001")
                .machineName("Assembly Machine")
                .machineType("ASSEMBLY")
                .process(process)
                .status(Machine.Status.RUNNING)
                .build();
        WorkOrder workOrder = WorkOrder.builder()
                .workOrderId(1L)
                .orderNo("WO-001")
                .item(item)
                .targetQty(10)
                .status(WorkOrder.Status.RUNNING)
                .build();
        Lot lot = Lot.builder()
                .lotId(1L)
                .lotNo("LOT-001")
                .workOrder(workOrder)
                .item(item)
                .currentProcess(process)
                .inputQty(10)
                .okQty(0)
                .ngQty(0)
                .status(Lot.Status.RUNNING)
                .build();
        return new Fixture(process, machine, workOrder, lot);
    }

    private record Fixture(Process process, Machine machine, WorkOrder workOrder, Lot lot) {
    }
}
