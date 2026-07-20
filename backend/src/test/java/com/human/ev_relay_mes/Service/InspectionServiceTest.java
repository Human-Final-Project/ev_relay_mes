package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.LotInspectionStandardSnapshot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Repository.InspectionRepository;
import com.human.ev_relay_mes.Repository.InspectionUnitResultRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.ProcessRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock InspectionRepository inspectionRepository;
    @Mock InspectionUnitResultRepository inspectionUnitResultRepository;
    @Mock MachineRepository machineRepository;
    @Mock ProcessRepository processRepository;
    @Mock LotRepository lotRepository;
    @Mock InspectionStandardService inspectionStandardService;
    @Mock ProductionService productionService;

    @InjectMocks InspectionService inspectionService;

    @Test
    void returnsExistingInspectionForDuplicateEventId() {
        Process process = Process.builder()
                .processCode("OP70").processName("test").processOrder(70).build();
        Machine machine = Machine.builder()
                .machineId("EQ-TEST-01").machineName("tester")
                .machineType("TESTER").process(process).build();
        Lot lot = Lot.builder().lotNo("LOT-001").build();
        LotInspectionStandardSnapshot snapshot = LotInspectionStandardSnapshot.builder()
                .lot(lot)
                .process(process)
                .inspectionItem("resistance")
                .itemName("resistance")
                .unit("OHM")
                .lowerLimit(BigDecimal.ZERO)
                .upperLimit(BigDecimal.TEN)
                .standardVersion(1)
                .build();
        Inspection existing = Inspection.builder()
                .inspectionId(13L)
                .eventId("inspection-001")
                .lot(lot)
                .machine(machine)
                .process(process)
                .standardSnapshot(snapshot)
                .inspectionItem("resistance")
                .result(Inspection.Result.OK)
                .build();
        InspectionResultReceiveRequestDto dto = new InspectionResultReceiveRequestDto();
        dto.setEventId("inspection-001");
        when(inspectionRepository.findByEventId("inspection-001"))
                .thenReturn(Optional.of(existing));

        var response = inspectionService.saveResult(dto);

        assertThat(response.getInspectionId()).isEqualTo(13L);
        verifyNoInteractions(machineRepository, processRepository, lotRepository);
    }

    @Test
    void aggregatesOp70ResultAfterAllMeasurementsForAllUnitsArrive() {
        Process process = Process.builder()
                .processCode("OP70").processName("검사").processOrder(70).build();
        Machine machine = Machine.builder()
                .machineId("EQ-TEST-01").machineName("검사기")
                .machineType("TESTER").process(process).build();
        Lot lot = Lot.builder()
                .lotNo("LOT-070").status(Lot.Status.RUNNING).currentProcess(process).build();
        LotInspectionStandardSnapshot snapshot = LotInspectionStandardSnapshot.builder()
                .lot(lot).process(process)
                .inspectionItem("CONTACT_RESISTANCE")
                .itemName("접촉 저항").unit("mOHM")
                .lowerLimit(BigDecimal.ZERO).upperLimit(new BigDecimal("50.000"))
                .standardVersion(1).build();

        InspectionResultReceiveRequestDto dto = new InspectionResultReceiveRequestDto();
        dto.setEventId("inspection-070-1-contact");
        dto.setLotNo("LOT-070");
        dto.setMachineId("EQ-TEST-01");
        dto.setProcessCode("OP70");
        dto.setUnitSeq(1);
        dto.setInspectionItem("CONTACT_RESISTANCE");
        dto.setMeasuredValue(new BigDecimal("40.000"));
        dto.setUnit("mOHM");

        Inspection voltage = measurement(lot, machine, process, snapshot,
                "OPERATION_VOLTAGE", Inspection.Result.OK);
        Inspection coil = measurement(lot, machine, process, snapshot,
                "COIL_RESISTANCE", Inspection.Result.OK);

        when(inspectionRepository.findByEventId(dto.getEventId())).thenReturn(Optional.empty());
        when(lotRepository.findByLotNoForUpdate("LOT-070")).thenReturn(Optional.of(lot));
        when(machineRepository.findById("EQ-TEST-01")).thenReturn(Optional.of(machine));
        when(processRepository.findById("OP70")).thenReturn(Optional.of(process));
        when(productionService.expectedInputQtyFor(lot, process)).thenReturn(1);
        when(inspectionStandardService.resolveSnapshot(lot, process, "CONTACT_RESISTANCE"))
                .thenReturn(snapshot);
        when(inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqAndInspectionItem(
                        "LOT-070", "OP70", 1, "CONTACT_RESISTANCE"))
                .thenReturn(Optional.empty());
        when(inspectionRepository.save(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inspectionStandardService.snapshotCount(lot, process)).thenReturn(3L);
        when(inspectionRepository.countByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                "LOT-070", "OP70", 1)).thenReturn(3L);
        when(inspectionUnitResultRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq("LOT-070", "OP70", 1))
                .thenReturn(Optional.empty());
        when(inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqOrderByInspectionIdAsc(
                        "LOT-070", "OP70", 1))
                .thenAnswer(invocation -> List.of(
                        voltage, coil, measurement(lot, machine, process, snapshot,
                                "CONTACT_RESISTANCE", Inspection.Result.OK)));
        when(inspectionUnitResultRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inspectionUnitResultRepository
                .countByLot_LotNoAndProcess_ProcessCode("LOT-070", "OP70"))
                .thenReturn(1L);
        when(inspectionUnitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndResult(
                        "LOT-070", "OP70", Inspection.Result.OK))
                .thenReturn(1L);
        when(inspectionUnitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndResult(
                        "LOT-070", "OP70", Inspection.Result.NG))
                .thenReturn(0L);

        var response = inspectionService.saveResult(dto);

        assertThat(response.getResult()).isEqualTo("OK");
        verify(productionService).completeInspectionProcess(
                lot, machine, process, 1, 1, 0);
    }

    private Inspection measurement(
            Lot lot, Machine machine, Process process,
            LotInspectionStandardSnapshot snapshot, String item, Inspection.Result result) {
        return Inspection.builder()
                .lot(lot).machine(machine).process(process).standardSnapshot(snapshot)
                .unitSeq(1).inspectionItem(item).measuredValue(BigDecimal.ONE)
                .unit(snapshot.getUnit()).result(result)
                .build();
    }
}
