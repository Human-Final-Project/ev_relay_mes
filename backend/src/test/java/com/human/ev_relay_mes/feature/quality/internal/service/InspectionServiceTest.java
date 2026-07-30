package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.production.api.ProductionOperations;
import com.human.ev_relay_mes.feature.quality.api.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.feature.quality.api.UnitJudgmentReceiveRequestDto;
import com.human.ev_relay_mes.feature.quality.api.Inspection;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResult;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.LotInspectionStandardSnapshot;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionRepository;
import com.human.ev_relay_mes.feature.quality.internal.repository.InspectionUnitResultRepository;
import com.human.ev_relay_mes.feature.production.api.ProductionData;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.masterdata.api.InspectionStandardOperations;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InspectionServiceTest {

    @Mock InspectionRepository inspectionRepository;
    @Mock InspectionUnitResultRepository inspectionUnitResultRepository;
    @Mock MachineRegistry machineRegistry;
    @Mock MasterDataLookup masterDataLookup;
    @Mock ProductionData productionData;
    @Mock InspectionStandardOperations inspectionStandardOperations;
    @Mock ProductionOperations productionOperations;
    @Mock DefectService defectService;

    InspectionService inspectionService;

    @BeforeEach
    void setUp() {
        InspectionUnitEvaluator unitEvaluator = new InspectionUnitEvaluator(
                inspectionRepository,
                inspectionUnitResultRepository,
                inspectionStandardOperations,
                productionOperations);
        inspectionService = new InspectionService(
                inspectionRepository,
                inspectionUnitResultRepository,
                machineRegistry,
                masterDataLookup,
                productionData,
                inspectionStandardOperations,
                productionOperations,
                new InspectionRules(),
                new InspectionDefectRecorder(defectService),
                unitEvaluator,
                new InspectionResponseAssembler());
    }

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
        verifyNoInteractions(machineRegistry, masterDataLookup, productionData);
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
        when(productionData.getRequiredLotForUpdate("LOT-070")).thenReturn(lot);
        when(machineRegistry.getRequiredMachine("EQ-TEST-01")).thenReturn(machine);
        when(masterDataLookup.getRequiredProcess("OP70")).thenReturn(process);
        when(productionOperations.expectedInputQtyFor(lot, process)).thenReturn(1);
        when(inspectionStandardOperations.resolveSnapshot(lot, process, "CONTACT_RESISTANCE"))
                .thenReturn(snapshot);
        when(inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqAndInspectionItem(
                        "LOT-070", "OP70", 1, "CONTACT_RESISTANCE"))
                .thenReturn(Optional.empty());
        when(inspectionRepository.save(any(Inspection.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inspectionStandardOperations.snapshotCount(lot, process)).thenReturn(3L);
        when(inspectionRepository.countByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
                "LOT-070", "OP70", 1)).thenReturn(3L);
        InspectionUnitResult unitResult = InspectionUnitResult.builder()
                .lot(lot).machine(machine).process(process).unitSeq(1)
                .l1Result(Inspection.Result.OK).build();
        when(inspectionUnitResultRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq("LOT-070", "OP70", 1))
                .thenReturn(Optional.of(unitResult));
        when(inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqOrderByInspectionIdAsc(
                        "LOT-070", "OP70", 1))
                .thenAnswer(invocation -> List.of(
                        voltage, coil, measurement(lot, machine, process, snapshot,
                                "CONTACT_RESISTANCE", Inspection.Result.OK)));
        when(inspectionUnitResultRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inspectionUnitResultRepository
                .countByLot_LotNoAndProcess_ProcessCodeAndEvaluationStatus(
                        "LOT-070", "OP70", InspectionUnitResult.EvaluationStatus.COMPLETED))
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
        verify(productionOperations).completeEvaluatedProcess(
                lot, machine, process, 1, 1, 0);
    }

    @Test
    void l1NgJudgmentCreatesDefectAndCompletesUnitWithoutMeasurements() {
        Process process = Process.builder()
                .processCode("OP40_OP50").processName("assembly").processOrder(40).build();
        Machine machine = Machine.builder()
                .machineId("EQ-ASSY-01").machineName("assembler")
                .machineType("ASSY").process(process).build();
        Lot lot = Lot.builder().lotNo("LOT-040").inputQty(2)
                .status(Lot.Status.RUNNING).currentProcess(process).build();
        UnitJudgmentReceiveRequestDto dto = new UnitJudgmentReceiveRequestDto();
        dto.setLotNo("LOT-040");
        dto.setMachineId("EQ-ASSY-01");
        dto.setProcessCode("OP40_OP50");
        dto.setUnitSeq(1);
        dto.setResult("NG");
        dto.setDefectCode("SPRING_MISSING_NG");

        when(productionData.getRequiredLotForUpdate("LOT-040")).thenReturn(lot);
        when(machineRegistry.getRequiredMachine("EQ-ASSY-01")).thenReturn(machine);
        when(masterDataLookup.getRequiredProcess("OP40_OP50")).thenReturn(process);
        when(productionOperations.expectedInputQtyFor(lot, process)).thenReturn(2);
        InspectionUnitResult persisted = InspectionUnitResult.builder()
                .lot(lot).machine(machine).process(process).unitSeq(1)
                .l1Result(Inspection.Result.NG).build();
        when(inspectionUnitResultRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeq("LOT-040", "OP40_OP50", 1))
                .thenReturn(Optional.empty(), Optional.of(persisted));
        when(inspectionUnitResultRepository.save(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inspectionStandardOperations.snapshotCount(lot, process)).thenReturn(0L);
        when(inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqOrderByInspectionIdAsc(
                        "LOT-040", "OP40_OP50", 1)).thenReturn(List.of());

        var response = inspectionService.saveJudgment(dto);

        assertThat(response.getL1Result()).isEqualTo("NG");
        assertThat(response.getMeasurementResult()).isEqualTo("OK");
        assertThat(response.getResult()).isEqualTo("NG");
        assertThat(response.getEvaluationStatus()).isEqualTo("COMPLETED");
        verify(defectService).createDefect(any());
    }

    @Test
    void rejectsDifferentValueForExistingUnitMeasurement() {
        Process process = process("OP70");
        Machine machine = machine("EQ-TEST-01", process);
        Lot lot = runningLot("LOT-DUPLICATE", process);
        LotInspectionStandardSnapshot snapshot = snapshot(lot, process, "mOHM");
        InspectionResultReceiveRequestDto dto = inspectionDto(
                "event-new", lot, machine, process, 1, "40.000", "mOHM");
        Inspection existing = Inspection.builder()
                .lot(lot)
                .machine(machine)
                .process(process)
                .standardSnapshot(snapshot)
                .unitSeq(1)
                .inspectionItem("CONTACT_RESISTANCE")
                .measuredValue(new BigDecimal("35.000"))
                .unit("mOHM")
                .result(Inspection.Result.OK)
                .build();
        when(inspectionRepository.findByEventId("event-new")).thenReturn(Optional.empty());
        when(productionData.getRequiredLotForUpdate(lot.getLotNo())).thenReturn(lot);
        when(machineRegistry.getRequiredMachine(machine.getMachineId())).thenReturn(machine);
        when(masterDataLookup.getRequiredProcess(process.getProcessCode())).thenReturn(process);
        when(productionOperations.expectedInputQtyFor(lot, process)).thenReturn(1);
        when(inspectionStandardOperations.resolveSnapshot(
                lot, process, "CONTACT_RESISTANCE")).thenReturn(snapshot);
        when(inspectionRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqAndInspectionItem(
                        lot.getLotNo(), process.getProcessCode(), 1, "CONTACT_RESISTANCE"))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> inspectionService.saveResult(dto))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_INSPECTION_MEASUREMENT);
    }

    @Test
    void rejectsMeasurementUnitThatDiffersFromSnapshot() {
        Process process = process("OP70");
        Machine machine = machine("EQ-TEST-01", process);
        Lot lot = runningLot("LOT-UNIT", process);
        LotInspectionStandardSnapshot snapshot = snapshot(lot, process, "mOHM");
        InspectionResultReceiveRequestDto dto = inspectionDto(
                "event-unit", lot, machine, process, 1, "40.000", "OHM");
        when(inspectionRepository.findByEventId("event-unit")).thenReturn(Optional.empty());
        when(productionData.getRequiredLotForUpdate(lot.getLotNo())).thenReturn(lot);
        when(machineRegistry.getRequiredMachine(machine.getMachineId())).thenReturn(machine);
        when(masterDataLookup.getRequiredProcess(process.getProcessCode())).thenReturn(process);
        when(productionOperations.expectedInputQtyFor(lot, process)).thenReturn(1);
        when(inspectionStandardOperations.resolveSnapshot(
                lot, process, "CONTACT_RESISTANCE")).thenReturn(snapshot);

        assertThatThrownBy(() -> inspectionService.saveResult(dto))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INSPECTION_VALUE);
    }

    @Test
    void rejectsInspectionUnitSequenceAboveExpectedInputQuantity() {
        Process process = process("OP70");
        Machine machine = machine("EQ-TEST-01", process);
        Lot lot = runningLot("LOT-SEQUENCE", process);
        InspectionResultReceiveRequestDto dto = inspectionDto(
                "event-sequence", lot, machine, process, 2, "40.000", "mOHM");
        when(inspectionRepository.findByEventId("event-sequence")).thenReturn(Optional.empty());
        when(productionData.getRequiredLotForUpdate(lot.getLotNo())).thenReturn(lot);
        when(machineRegistry.getRequiredMachine(machine.getMachineId())).thenReturn(machine);
        when(masterDataLookup.getRequiredProcess(process.getProcessCode())).thenReturn(process);
        when(productionOperations.expectedInputQtyFor(lot, process)).thenReturn(1);

        assertThatThrownBy(() -> inspectionService.saveResult(dto))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INSPECTION_UNIT_SEQ);

        verifyNoInteractions(inspectionStandardOperations);
    }

    @Test
    void rejectsL1NgJudgmentWithoutDefectCode() {
        Process process = process("OP40_OP50");
        Machine machine = machine("EQ-ASSY-01", process);
        Lot lot = runningLot("LOT-NG-NO-CODE", process);
        UnitJudgmentReceiveRequestDto dto = new UnitJudgmentReceiveRequestDto();
        dto.setLotNo(lot.getLotNo());
        dto.setMachineId(machine.getMachineId());
        dto.setProcessCode(process.getProcessCode());
        dto.setUnitSeq(1);
        dto.setResult("NG");
        when(productionData.getRequiredLotForUpdate(lot.getLotNo())).thenReturn(lot);
        when(machineRegistry.getRequiredMachine(machine.getMachineId())).thenReturn(machine);
        when(masterDataLookup.getRequiredProcess(process.getProcessCode())).thenReturn(process);
        when(productionOperations.expectedInputQtyFor(lot, process)).thenReturn(1);

        assertThatThrownBy(() -> inspectionService.saveJudgment(dto))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verifyNoInteractions(defectService);
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

    private Process process(String processCode) {
        return Process.builder()
                .processCode(processCode)
                .processName("test process")
                .processOrder(70)
                .build();
    }

    private Machine machine(String machineId, Process process) {
        return Machine.builder()
                .machineId(machineId)
                .machineName("test machine")
                .machineType("TESTER")
                .process(process)
                .build();
    }

    private Lot runningLot(String lotNo, Process process) {
        return Lot.builder()
                .lotNo(lotNo)
                .inputQty(1)
                .status(Lot.Status.RUNNING)
                .currentProcess(process)
                .build();
    }

    private LotInspectionStandardSnapshot snapshot(
            Lot lot, Process process, String unit) {
        return LotInspectionStandardSnapshot.builder()
                .lot(lot)
                .process(process)
                .inspectionItem("CONTACT_RESISTANCE")
                .itemName("접촉 저항")
                .unit(unit)
                .lowerLimit(BigDecimal.ZERO)
                .upperLimit(new BigDecimal("50.000"))
                .standardVersion(1)
                .build();
    }

    private InspectionResultReceiveRequestDto inspectionDto(
            String eventId, Lot lot, Machine machine, Process process,
            int unitSeq, String measuredValue, String unit) {
        InspectionResultReceiveRequestDto dto = new InspectionResultReceiveRequestDto();
        dto.setEventId(eventId);
        dto.setLotNo(lot.getLotNo());
        dto.setMachineId(machine.getMachineId());
        dto.setProcessCode(process.getProcessCode());
        dto.setUnitSeq(unitSeq);
        dto.setInspectionItem("CONTACT_RESISTANCE");
        dto.setMeasuredValue(new BigDecimal(measuredValue));
        dto.setUnit(unit);
        return dto;
    }
}
