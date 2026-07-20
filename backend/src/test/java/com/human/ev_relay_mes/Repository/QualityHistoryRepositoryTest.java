package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Entity.DefectCode;
import com.human.ev_relay_mes.Entity.DefectHistory;
import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.LotInspectionStandardSnapshot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineAlarmHistory;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.WorkOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class QualityHistoryRepositoryTest extends RepositoryTestSupport {

    @Autowired DefectHistoryRepository defectHistoryRepository;
    @Autowired InspectionRepository inspectionRepository;
    @Autowired MachineAlarmHistoryRepository machineAlarmHistoryRepository;
    @Autowired MachineStatusHistoryRepository machineStatusHistoryRepository;

    @Test
    void 검사결과를_연관키와_판정_기간으로_조회한다() {
        Fixture fixture = fixture();
        Inspection ok = inspectionRepository.saveAndFlush(inspection(fixture, Inspection.Result.OK));
        inspectionRepository.saveAndFlush(inspection(fixture, Inspection.Result.NG));

        assertThat(inspectionRepository.findByLot_LotNoOrderByInspectedAtDesc(fixture.lot.getLotNo())).hasSize(2);
        assertThat(inspectionRepository.findByMachine_MachineIdOrderByInspectedAtDesc(fixture.machine.getMachineId())).hasSize(2);
        assertThat(inspectionRepository.findByProcess_ProcessCodeOrderByInspectedAtDesc(fixture.process.getProcessCode())).hasSize(2);
        assertThat(inspectionRepository.findByResultOrderByInspectedAtDesc(Inspection.Result.OK))
                .extracting(Inspection::getInspectionId).containsExactly(ok.getInspectionId());
        assertThat(inspectionRepository.findByInspectedAtBetweenOrderByInspectedAtDesc(
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1))).hasSize(2);
    }

    @Test
    void 불량이력을_불량코드와_미확인상태로_조회한다() {
        Fixture fixture = fixture();
        DefectCode code = entityManager.persistAndFlush(DefectCode.builder()
                .defectCode("DEFECT-01")
                .defectName("test defect")
                .process(fixture.process)
                .build());
        DefectHistory unconfirmed = defectHistoryRepository.saveAndFlush(defect(fixture, code, null));
        defectHistoryRepository.saveAndFlush(defect(
                fixture, code, member("inspector", Member.Role.OPERATOR, Member.Status.ACTIVE)));

        assertThat(defectHistoryRepository.findByLot_LotNoOrderByOccurredAtDesc(fixture.lot.getLotNo())).hasSize(2);
        assertThat(defectHistoryRepository.findByMachine_MachineIdOrderByOccurredAtDesc(fixture.machine.getMachineId())).hasSize(2);
        assertThat(defectHistoryRepository.findByProcess_ProcessCodeOrderByOccurredAtDesc(fixture.process.getProcessCode())).hasSize(2);
        assertThat(defectHistoryRepository.findByDefectCode_DefectCodeOrderByOccurredAtDesc("DEFECT-01")).hasSize(2);
        assertThat(defectHistoryRepository.findByConfirmedByIsNullOrderByOccurredAtDesc())
                .extracting(DefectHistory::getDefectHistoryId)
                .containsExactly(unconfirmed.getDefectHistoryId());
    }

    @Test
    void 설비상태이력을_상태와_연관키_기간으로_조회한다() {
        Fixture fixture = fixture();
        MachineStatusHistory running = machineStatusHistoryRepository.saveAndFlush(status(fixture, Machine.Status.RUNNING));
        machineStatusHistoryRepository.saveAndFlush(status(fixture, Machine.Status.ERROR));

        assertThat(machineStatusHistoryRepository.findByMachine_MachineIdOrderByRecordedAtDesc(fixture.machine.getMachineId())).hasSize(2);
        assertThat(machineStatusHistoryRepository.findByLot_LotNoOrderByRecordedAtDesc(fixture.lot.getLotNo())).hasSize(2);
        assertThat(machineStatusHistoryRepository.findByProcess_ProcessCodeOrderByRecordedAtDesc(fixture.process.getProcessCode())).hasSize(2);
        assertThat(machineStatusHistoryRepository.findByStatusOrderByRecordedAtDesc(Machine.Status.RUNNING))
                .extracting(MachineStatusHistory::getMachineStatusHistoryId)
                .containsExactly(running.getMachineStatusHistoryId());
        assertThat(machineStatusHistoryRepository.findByRecordedAtBetweenOrderByRecordedAtDesc(
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1))).hasSize(2);
    }

    @Test
    void 설비알람을_미해제와_심각도_중복조건으로_조회한다() {
        Fixture fixture = fixture();
        AlarmCode code = entityManager.persistAndFlush(AlarmCode.builder()
                .alarmCode("ALARM-01")
                .alarmName("test alarm")
                .machineType("TESTER")
                .build());
        MachineAlarmHistory open = machineAlarmHistoryRepository.saveAndFlush(alarm(fixture, code, "ERROR", null));
        MachineAlarmHistory cleared = machineAlarmHistoryRepository.saveAndFlush(
                alarm(fixture, code, "WARN", LocalDateTime.now()));

        assertThat(machineAlarmHistoryRepository.findByIdForUpdate(open.getMachineAlarmHistoryId())).isPresent();
        assertThat(machineAlarmHistoryRepository.findByMachine_MachineIdOrderByOccurredAtDesc(fixture.machine.getMachineId())).hasSize(2);
        assertThat(machineAlarmHistoryRepository.findByAlarmCode_AlarmCodeOrderByOccurredAtDesc("ALARM-01")).hasSize(2);
        assertThat(machineAlarmHistoryRepository.findByClearedAtIsNullOrderByOccurredAtDesc())
                .extracting(MachineAlarmHistory::getMachineAlarmHistoryId)
                .containsExactly(open.getMachineAlarmHistoryId());
        assertThat(machineAlarmHistoryRepository.findByClearedAtIsNotNullOrderByClearedAtDesc())
                .extracting(MachineAlarmHistory::getMachineAlarmHistoryId)
                .containsExactly(cleared.getMachineAlarmHistoryId());
        assertThat(machineAlarmHistoryRepository
                .existsByMachine_MachineIdAndAlarmLevelIgnoreCaseAndClearedAtIsNullAndMachineAlarmHistoryIdNot(
                        fixture.machine.getMachineId(), "error", -1L)).isTrue();
    }

    private Fixture fixture() {
        long suffix = System.nanoTime();
        Item product = item("FG-" + suffix, Item.ItemType.FG);
        Process process = process("OP-" + suffix, Math.abs((int) (suffix % 100000)));
        Machine machine = machine("EQ-" + suffix, process, Machine.Status.IDLE, "Y");
        WorkOrder order = workOrder("WO-" + suffix, product, WorkOrder.Status.RELEASED, 100);
        Lot lot = lot("LOT-" + suffix, order, product, process, Lot.Status.RUNNING, 100);
        LotInspectionStandardSnapshot snapshot = entityManager.persistAndFlush(
                LotInspectionStandardSnapshot.builder()
                        .lot(lot)
                        .process(process)
                        .inspectionItem("resistance")
                        .itemName("resistance")
                        .unit("OHM")
                        .lowerLimit(BigDecimal.ZERO)
                        .upperLimit(BigDecimal.TEN)
                        .standardVersion(1)
                        .build());
        return new Fixture(process, machine, lot, snapshot);
    }

    private Inspection inspection(Fixture fixture, Inspection.Result result) {
        return Inspection.builder()
                .lot(fixture.lot)
                .machine(fixture.machine)
                .process(fixture.process)
                .standardSnapshot(fixture.snapshot)
                .unitSeq(result == Inspection.Result.OK ? 1 : 2)
                .inspectionItem("resistance")
                .measuredValue(BigDecimal.ONE)
                .unit("OHM")
                .lowerLimit(BigDecimal.ZERO)
                .upperLimit(BigDecimal.TEN)
                .standardVersion(1)
                .result(result)
                .build();
    }

    private DefectHistory defect(Fixture fixture, DefectCode code, Member confirmedBy) {
        return DefectHistory.builder()
                .lot(fixture.lot)
                .machine(fixture.machine)
                .process(fixture.process)
                .defectCode(code)
                .defectQty(1)
                .confirmedBy(confirmedBy)
                .build();
    }

    private MachineStatusHistory status(Fixture fixture, Machine.Status status) {
        return MachineStatusHistory.builder()
                .machine(fixture.machine)
                .lot(fixture.lot)
                .process(fixture.process)
                .status(status)
                .build();
    }

    private MachineAlarmHistory alarm(
            Fixture fixture, AlarmCode code, String level, LocalDateTime clearedAt) {
        return MachineAlarmHistory.builder()
                .machine(fixture.machine)
                .alarmCode(code)
                .alarmLevel(level)
                .clearedAt(clearedAt)
                .build();
    }

    private record Fixture(Process process, Machine machine, Lot lot, LotInspectionStandardSnapshot snapshot) {}
}
