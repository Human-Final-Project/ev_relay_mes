package com.human.ev_relay_mes.feature.dashboard.internal.service;

import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.material.api.MaterialLot;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardQuery;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardSummary;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardSummary.AlarmSummary;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardSummary.MachineSummary;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardSummary.MaterialSummary;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardSummary.ProductionSummary;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardSummary.QualitySummary;
import com.human.ev_relay_mes.feature.dashboard.api.DashboardSummary.WorkOrderSummary;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmOperations;
import com.human.ev_relay_mes.feature.machine.api.MachineRegistry;
import com.human.ev_relay_mes.feature.material.api.MaterialInventory;
import com.human.ev_relay_mes.feature.quality.api.QualityMetrics;
import com.human.ev_relay_mes.Repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService implements DashboardQuery {

    private static final int LOW_STOCK_THRESHOLD = 100;

    private final LotRepository lotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MachineRegistry machineRegistry;
    private final QualityMetrics qualityMetrics;
    private final MachineAlarmOperations machineAlarmOperations;
    private final MaterialInventory materialInventory;

    @Override
    public DashboardSummary getSummary() {
        LocalDateTime startAt = LocalDate.now().atStartOfDay();
        LocalDateTime endAt = startAt.plusDays(1);

        List<Lot> terminalLots = lotRepository.findAll().stream()
                .filter(lot -> lot.getStatus() == Lot.Status.COMPLETED
                        || lot.getStatus() == Lot.Status.SCRAPPED)
                .filter(lot -> lot.getCompletedAt() != null
                        && !lot.getCompletedAt().isBefore(startAt)
                        && lot.getCompletedAt().isBefore(endAt))
                .toList();

        ProductionSummary production = new ProductionSummary(
                terminalLots.size(),
                terminalLots.stream().mapToInt(lot -> valueOrZero(lot.getOkQty())).sum(),
                terminalLots.stream().mapToInt(lot -> valueOrZero(lot.getNgQty())).sum());

        List<WorkOrder> workOrders = workOrderRepository.findAll();
        WorkOrderSummary workOrderSummary = new WorkOrderSummary(
                workOrders.size(),
                count(workOrders, WorkOrder.Status.CREATED),
                count(workOrders, WorkOrder.Status.RELEASED),
                count(workOrders, WorkOrder.Status.RUNNING),
                count(workOrders, WorkOrder.Status.COMPLETED),
                count(workOrders, WorkOrder.Status.CANCELED));

        List<Machine> machines = machineRegistry.getAllMachines();
        MachineSummary machineSummary = new MachineSummary(
                machines.size(),
                countMachines(machines, Machine.Status.IDLE),
                countMachines(machines, Machine.Status.RUNNING),
                countMachines(machines, Machine.Status.ERROR),
                countMachines(machines, Machine.Status.STOPPED));

        QualityMetrics.QualityPeriodSummary qualityPeriod =
                qualityMetrics.summarizePeriod(startAt, endAt);
        QualitySummary quality = new QualitySummary(
                qualityPeriod.okInspections(),
                qualityPeriod.ngInspections(),
                qualityPeriod.defectQty());

        AlarmSummary alarms = new AlarmSummary(
                machineAlarmOperations.countActiveAlarms(),
                machineAlarmOperations.countAlarmsOccurredBetween(startAt, endAt));

        MaterialSummary materials = summarizeMaterials(materialInventory.getMaterialLotEntities());

        return new DashboardSummary(
                production, workOrderSummary, machineSummary, quality, alarms, materials,
                LocalDateTime.now());
    }

    private MaterialSummary summarizeMaterials(List<MaterialLot> lots) {
        Map<String, Integer> availableByItem = new HashMap<>();
        int heldQty = 0;

        for (MaterialLot lot : lots) {
            int currentQty = valueOrZero(lot.getCurrentQty());
            if (lot.getStatus() == MaterialLot.Status.AVAILABLE) {
                availableByItem.merge(lot.getItem().getItemCode(), currentQty, Integer::sum);
            } else if (lot.getStatus() == MaterialLot.Status.HOLD) {
                heldQty += currentQty;
            }
        }

        long lowStockItems = availableByItem.values().stream()
                .filter(quantity -> quantity <= LOW_STOCK_THRESHOLD)
                .count();
        int availableQty = availableByItem.values().stream().mapToInt(Integer::intValue).sum();
        return new MaterialSummary(
                availableByItem.size(), lowStockItems, availableQty, heldQty, LOW_STOCK_THRESHOLD);
    }

    private long count(List<WorkOrder> orders, WorkOrder.Status status) {
        return orders.stream().filter(order -> order.getStatus() == status).count();
    }

    private long countMachines(List<Machine> machines, Machine.Status status) {
        return machines.stream().filter(machine -> machine.getStatus() == status).count();
    }

    private static int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }

}
