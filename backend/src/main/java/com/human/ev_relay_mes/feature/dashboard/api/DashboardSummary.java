package com.human.ev_relay_mes.feature.dashboard.api;

import java.time.LocalDateTime;

public record DashboardSummary(
        ProductionSummary production,
        WorkOrderSummary workOrders,
        MachineSummary machines,
        QualitySummary quality,
        AlarmSummary alarms,
        MaterialSummary materials,
        LocalDateTime generatedAt) {

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
