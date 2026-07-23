package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Entity.Inspection;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MaterialLot;
import com.human.ev_relay_mes.Entity.WorkOrder;
import com.human.ev_relay_mes.Repository.DefectHistoryRepository;
import com.human.ev_relay_mes.Repository.InspectionRepository;
import com.human.ev_relay_mes.Repository.LotRepository;
import com.human.ev_relay_mes.Repository.MachineAlarmHistoryRepository;
import com.human.ev_relay_mes.Repository.MachineRepository;
import com.human.ev_relay_mes.Repository.MaterialLotRepository;
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
public class DashboardService {

    private static final int LOW_STOCK_THRESHOLD = 100;

    private final LotRepository lotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final MachineRepository machineRepository;
    private final InspectionRepository inspectionRepository;
    private final DefectHistoryRepository defectHistoryRepository;
    private final MachineAlarmHistoryRepository machineAlarmHistoryRepository;
    private final MaterialLotRepository materialLotRepository;

    public DashboardSummary getSummary() {
        LocalDateTime startAt = LocalDate.now().atStartOfDay();
        LocalDateTime endAt = startAt.plusDays(1);

        List<Lot> completedLots = lotRepository.findByStatusOrderByCreatedAtDesc(Lot.Status.COMPLETED)
                .stream()
                .filter(lot -> lot.getCompletedAt() != null
                        && !lot.getCompletedAt().isBefore(startAt)
                        && lot.getCompletedAt().isBefore(endAt))
                .toList();

        ProductionSummary production = new ProductionSummary(
                completedLots.size(),
                completedLots.stream().mapToInt(lot -> valueOrZero(lot.getOkQty())).sum(),
                completedLots.stream().mapToInt(lot -> valueOrZero(lot.getNgQty())).sum());

        List<WorkOrder> workOrders = workOrderRepository.findAll();
        WorkOrderSummary workOrderSummary = new WorkOrderSummary(
                workOrders.size(),
                count(workOrders, WorkOrder.Status.CREATED),
                count(workOrders, WorkOrder.Status.RELEASED),
                count(workOrders, WorkOrder.Status.RUNNING),
                count(workOrders, WorkOrder.Status.COMPLETED),
                count(workOrders, WorkOrder.Status.CANCELED));

        List<Machine> machines = machineRepository.findAll();
        MachineSummary machineSummary = new MachineSummary(
                machines.size(),
                countMachines(machines, Machine.Status.IDLE),
                countMachines(machines, Machine.Status.RUNNING),
                countMachines(machines, Machine.Status.ERROR),
                countMachines(machines, Machine.Status.STOPPED));

        List<Inspection> inspections = inspectionRepository
                .findByInspectedAtBetweenOrderByInspectedAtDesc(startAt, endAt);
        QualitySummary quality = new QualitySummary(
                inspections.stream().filter(i -> i.getResult() == Inspection.Result.OK).count(),
                inspections.stream().filter(i -> i.getResult() == Inspection.Result.NG).count(),
                defectHistoryRepository.findByOccurredAtBetweenOrderByOccurredAtDesc(startAt, endAt)
                        .stream().mapToInt(defect -> valueOrZero(defect.getDefectQty())).sum());

        AlarmSummary alarms = new AlarmSummary(
                machineAlarmHistoryRepository.findByClearedAtIsNullOrderByOccurredAtDesc().size(),
                machineAlarmHistoryRepository
                        .findByOccurredAtBetweenOrderByOccurredAtDesc(startAt, endAt).size());

        MaterialSummary materials = summarizeMaterials(materialLotRepository.findAll());

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

    public record DashboardSummary(
            ProductionSummary production,
            WorkOrderSummary workOrders,
            MachineSummary machines,
            QualitySummary quality,
            AlarmSummary alarms,
            MaterialSummary materials,
            LocalDateTime generatedAt) {
    }

    public record ProductionSummary(long completedLots, int okQty, int ngQty) {
    }

    public record WorkOrderSummary(
            long total, long created, long released, long running, long completed, long canceled) {
    }

    public record MachineSummary(long total, long idle, long running, long error, long stopped) {
    }

    public record QualitySummary(long okInspections, long ngInspections, int defectQty) {
    }

    public record AlarmSummary(long active, long occurredToday) {
    }

    public record MaterialSummary(
            long availableItemCount,
            long lowStockItemCount,
            int availableQty,
            int heldQty,
            int lowStockThreshold) {
    }
}
