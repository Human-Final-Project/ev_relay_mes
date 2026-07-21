package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MaterialLot;
import com.human.ev_relay_mes.Entity.Process;
import com.human.ev_relay_mes.Entity.ProductionLog;
import com.human.ev_relay_mes.Entity.WorkCommand;
import com.human.ev_relay_mes.Entity.WorkOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class ProductionRepositoryTest extends RepositoryTestSupport {

    @Autowired LotRepository lotRepository;
    @Autowired MachineRepository machineRepository;
    @Autowired MaterialLotRepository materialLotRepository;
    @Autowired ProductionLogRepository productionLogRepository;
    @Autowired WorkCommandRepository workCommandRepository;
    @Autowired WorkOrderRepository workOrderRepository;

    @Test
    void 작업지시와_LOT을_상태로_조회하고_수량을_합산한다() {
        Item product = item("FG-001", Item.ItemType.FG);
        Process op10 = process("OP10", 10);
        WorkOrder order = workOrder("WO-001", product, WorkOrder.Status.RELEASED, 100);
        Lot waiting = lot("LOT-001", order, product, op10, Lot.Status.WAITING, 40);
        waiting.setStartRequestedAt(LocalDateTime.now().minusMinutes(1));
        lotRepository.saveAndFlush(waiting);
        Lot completed = lot("LOT-002", order, product, op10, Lot.Status.COMPLETED, 30);
        completed.setOkQty(25);
        completed.setNgQty(5);
        lotRepository.saveAndFlush(completed);
        lot("LOT-003", order, product, op10, Lot.Status.SCRAPPED, 10);

        assertThat(workOrderRepository.existsByOrderNo("WO-001")).isTrue();
        assertThat(workOrderRepository.findByIdForUpdate(order.getWorkOrderId())).isPresent();
        assertThat(lotRepository.findByLotNoForUpdate("LOT-001")).isPresent();
        assertThat(lotRepository.existsByWorkOrder_WorkOrderIdAndStatusIn(
                order.getWorkOrderId(), List.of(Lot.Status.WAITING, Lot.Status.RUNNING))).isTrue();
        assertThat(lotRepository.findByStatusAndStartRequestedAtIsNotNullOrderByStartRequestedAtAsc(Lot.Status.WAITING))
                .extracting(Lot::getLotNo).containsExactly("LOT-001");
        assertThat(lotRepository.sumInputQtyByWorkOrderId(order.getWorkOrderId())).isEqualTo(80L);
        assertThat(lotRepository.sumInputQtyByWorkOrderIdExcludingStatus(
                order.getWorkOrderId(), Lot.Status.SCRAPPED)).isEqualTo(70L);
        assertThat(lotRepository.sumInputQtyByWorkOrderIdAndStatus(
                order.getWorkOrderId(), Lot.Status.COMPLETED)).isEqualTo(30L);
        assertThat(lotRepository.sumOkQtyByWorkOrderIdAndStatus(
                order.getWorkOrderId(), Lot.Status.COMPLETED)).isEqualTo(25L);
        assertThat(lotRepository.findMaxProductionRoundByWorkOrderId(
                order.getWorkOrderId())).isEqualTo(1);
        assertThat(lotRepository.countLineBlockingLots(
                List.of(Lot.Status.RUNNING, Lot.Status.HOLD), Lot.Status.WAITING)).isEqualTo(1L);
    }

    @Test
    void 가용_자재LOT만_FIFO_순서로_잠금조회한다() {
        Item material = item("RM-001", Item.ItemType.RM);
        MaterialLot first = materialLotRepository.saveAndFlush(materialLot("ML-001", material, 10, MaterialLot.Status.AVAILABLE));
        MaterialLot second = materialLotRepository.saveAndFlush(materialLot("ML-002", material, 20, MaterialLot.Status.AVAILABLE));
        materialLotRepository.saveAndFlush(materialLot("ML-003", material, 0, MaterialLot.Status.AVAILABLE));
        materialLotRepository.saveAndFlush(materialLot("ML-004", material, 30, MaterialLot.Status.HOLD));

        assertThat(materialLotRepository.existsByMaterialLotNo("ML-001")).isTrue();
        assertThat(materialLotRepository.findAvailableLotsForUpdate("RM-001", MaterialLot.Status.AVAILABLE))
                .extracting(MaterialLot::getMaterialLotId)
                .containsExactly(first.getMaterialLotId(), second.getMaterialLotId());
        assertThat(materialLotRepository.sumAvailableQty(
                "RM-001", MaterialLot.Status.AVAILABLE)).isEqualTo(30L);
    }

    @Test
    void 작업명령을_상태와_연관키로_조회한다() {
        Fixture fixture = fixture();
        WorkCommand first = workCommandRepository.saveAndFlush(command(fixture, WorkCommand.Status.PENDING));
        WorkCommand second = workCommandRepository.saveAndFlush(command(fixture, WorkCommand.Status.DISPATCHED));

        assertThat(workCommandRepository.findByIdForUpdate(first.getCommandId())).isPresent();
        assertThat(workCommandRepository.findByStatusForUpdate(WorkCommand.Status.PENDING))
                .extracting(WorkCommand::getCommandId).containsExactly(first.getCommandId());
        assertThat(workCommandRepository.existsByMachine_MachineIdAndStatusIn(
                fixture.machine.getMachineId(), List.of(WorkCommand.Status.PENDING))).isTrue();
        assertThat(workCommandRepository.countByMachine_MachineIdAndStatusIn(
                fixture.machine.getMachineId(), List.of(WorkCommand.Status.PENDING, WorkCommand.Status.DISPATCHED)))
                .isEqualTo(2);
        assertThat(workCommandRepository.existsByLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusIn(
                fixture.lot.getLotNo(), fixture.process.getProcessCode(), WorkCommand.CommandType.START,
                List.of(WorkCommand.Status.PENDING))).isTrue();
        assertThat(workCommandRepository.findByLot_LotNoOrderByCreatedAtAsc(fixture.lot.getLotNo()))
                .extracting(WorkCommand::getCommandId)
                .containsExactly(first.getCommandId(), second.getCommandId());
    }

    @Test
    void 생산실적을_LOT_설비_공정_상태_기간으로_조회한다() {
        Fixture fixture = fixture();
        ProductionLog completed = productionLogRepository.saveAndFlush(log(fixture, "COMPLETED"));
        productionLogRepository.saveAndFlush(log(fixture, "FAILED"));

        assertThat(productionLogRepository.findByLot_LotNoOrderByCreatedAtDesc(fixture.lot.getLotNo())).hasSize(2);
        assertThat(productionLogRepository.findByMachine_MachineIdOrderByCreatedAtDesc(fixture.machine.getMachineId())).hasSize(2);
        assertThat(productionLogRepository.findByProcess_ProcessCodeOrderByCreatedAtDesc(fixture.process.getProcessCode())).hasSize(2);
        assertThat(productionLogRepository.findByStatusOrderByCreatedAtDesc("COMPLETED"))
                .extracting(ProductionLog::getProductionLogId)
                .containsExactly(completed.getProductionLogId());
        assertThat(productionLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                LocalDateTime.now().minusMinutes(1), LocalDateTime.now().plusMinutes(1))).hasSize(2);
    }

    private Fixture fixture() {
        Item product = item("FG-" + System.nanoTime(), Item.ItemType.FG);
        Process process = process("OP-" + System.nanoTime(), Math.abs((int) (System.nanoTime() % 100000)));
        Machine machine = machine("EQ-" + System.nanoTime(), process, Machine.Status.IDLE);
        WorkOrder order = workOrder("WO-" + System.nanoTime(), product, WorkOrder.Status.RELEASED, 100);
        Lot lot = lot("LOT-" + System.nanoTime(), order, product, process, Lot.Status.WAITING, 100);
        return new Fixture(process, machine, lot);
    }

    private MaterialLot materialLot(String no, Item item, int qty, MaterialLot.Status status) {
        return MaterialLot.builder()
                .materialLotNo(no)
                .item(item)
                .receivedQty(Math.max(qty, 1))
                .currentQty(qty)
                .status(status)
                .build();
    }

    private WorkCommand command(Fixture fixture, WorkCommand.Status status) {
        return WorkCommand.builder()
                .commandType(WorkCommand.CommandType.START)
                .machine(fixture.machine)
                .process(fixture.process)
                .lot(fixture.lot)
                .inputQty(10)
                .status(status)
                .build();
    }

    private ProductionLog log(Fixture fixture, String status) {
        return ProductionLog.builder()
                .lot(fixture.lot)
                .machine(fixture.machine)
                .process(fixture.process)
                .inputQty(10)
                .okQty(9)
                .ngQty(1)
                .status(status)
                .build();
    }

    private record Fixture(Process process, Machine machine, Lot lot) {}
}
