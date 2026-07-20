package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.AlarmCode;
import com.human.ev_relay_mes.Entity.Bom;
import com.human.ev_relay_mes.Entity.DefectCode;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.Process;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MasterDataRepositoryTest extends RepositoryTestSupport {

    @Autowired AlarmCodeRepository alarmCodeRepository;
    @Autowired BomRepository bomRepository;
    @Autowired DefectCodeRepository defectCodeRepository;
    @Autowired ItemRepository itemRepository;
    @Autowired MachineRepository machineRepository;
    @Autowired ProcessRepository processRepository;

    @Test
    void 알람코드를_설비유형과_사용여부로_조회한다() {
        alarmCodeRepository.saveAllAndFlush(java.util.List.of(
                alarm("B_ERROR", "TESTER"),
                alarm("A_ERROR", "TESTER"),
                alarm("C_ERROR", "WINDER"),
                alarm("D_ERROR", "TESTER")));

        assertThat(alarmCodeRepository.findAllById(java.util.List.of("A_ERROR", "B_ERROR")))
                .extracting(AlarmCode::getAlarmCode)
                .containsExactlyInAnyOrder("A_ERROR", "B_ERROR");
        assertThat(alarmCodeRepository.existsByAlarmCode("A_ERROR")).isTrue();
    }

    @Test
    void 공정의_순서와_이전_다음_공정을_조회한다() {
        process("OP10", 10);
        process("OP20", 20);
        processRepository.saveAndFlush(Process.builder()
                .processCode("OP30")
                .processName("third process")
                .processOrder(30)
                .build());

        assertThat(processRepository.existsByProcessOrder(20)).isTrue();
        assertThat(processRepository.existsByProcessOrderAndProcessCodeNot(20, "OP10")).isTrue();
        assertThat(processRepository.findFirstByOrderByProcessOrderAsc())
                .get().extracting(Process::getProcessCode).isEqualTo("OP10");
        assertThat(processRepository.findFirstByProcessOrderGreaterThanOrderByProcessOrderAsc(10))
                .get().extracting(Process::getProcessCode).isEqualTo("OP20");
        assertThat(processRepository.findFirstByProcessOrderLessThanOrderByProcessOrderDesc(20))
                .get().extracting(Process::getProcessCode).isEqualTo("OP10");
    }

    @Test
    void 불량코드와_설비를_공정별로_조회한다() {
        Process op20 = process("OP20", 20);
        Process op30 = process("OP30", 30);
        defectCodeRepository.saveAllAndFlush(java.util.List.of(
                defect("B_NG", op20),
                defect("A_NG", op20),
                defect("C_NG", op30),
                defect("D_NG", op20)));
        machine("EQ-02", op20, Machine.Status.IDLE);
        machine("EQ-01", op20, Machine.Status.RUNNING);
        machine("EQ-03", op20, Machine.Status.ERROR);

        assertThat(defectCodeRepository.findAllById(java.util.List.of("A_NG", "B_NG", "D_NG")))
                .extracting(DefectCode::getDefectCode)
                .containsExactlyInAnyOrder("A_NG", "B_NG", "D_NG");
        assertThat(machineRepository.findUsableByProcessForUpdate("OP20"))
                .extracting(Machine::getMachineId)
                .containsExactly("EQ-01", "EQ-02", "EQ-03");
        assertThat(machineRepository.findByStatusOrderByMachineIdAsc(Machine.Status.RUNNING))
                .extracting(Machine::getMachineId)
                .containsExactly("EQ-01");
    }

    @Test
    void 품목을_저장하고_BOM_중복과_활성_구성품을_조회한다() {
        Item parent = item("FG-001", Item.ItemType.FG);
        Item childB = item("RM-002", Item.ItemType.RM);
        Item childA = item("RM-001", Item.ItemType.RM);
        Process process = process("OP40", 40);
        Bom activeB = bomRepository.saveAndFlush(bom(parent, childB, process, "Y"));
        bomRepository.saveAndFlush(bom(parent, childA, process, "Y"));
        bomRepository.saveAndFlush(bom(parent, item("RM-003", Item.ItemType.RM), process, "N"));

        assertThat(itemRepository.findById("FG-001")).isPresent();
        assertThat(bomRepository.findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc("FG-001", "Y"))
                .extracting(value -> value.getChildItem().getItemCode())
                .containsExactly("RM-001", "RM-002");
        assertThat(bomRepository.existsByParentItem_ItemCodeAndChildItem_ItemCodeAndProcess_ProcessCode(
                "FG-001", "RM-002", "OP40")).isTrue();
        assertThat(bomRepository.existsByParentItem_ItemCodeAndChildItem_ItemCodeAndProcess_ProcessCodeAndBomIdNot(
                "FG-001", "RM-002", "OP40", activeB.getBomId())).isFalse();
    }

    private AlarmCode alarm(String code, String machineType) {
        return AlarmCode.builder()
                .alarmCode(code)
                .alarmName(code + " alarm")
                .machineType(machineType)
                .build();
    }

    private DefectCode defect(String code, Process process) {
        return DefectCode.builder()
                .defectCode(code)
                .defectName(code + " defect")
                .process(process)
                .build();
    }

    private Bom bom(Item parent, Item child, Process process, String useYn) {
        return Bom.builder()
                .parentItem(parent)
                .childItem(child)
                .process(process)
                .quantity(BigDecimal.ONE)
                .useYn(useYn)
                .build();
    }
}
