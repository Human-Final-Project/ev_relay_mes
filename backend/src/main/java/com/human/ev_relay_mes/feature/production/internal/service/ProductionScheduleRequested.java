package com.human.ev_relay_mes.feature.production.internal.service;

record ProductionScheduleRequested(Target target, String key) {

    static ProductionScheduleRequested forLot(String lotNo) {
        return new ProductionScheduleRequested(Target.LOT, lotNo);
    }

    static ProductionScheduleRequested forMachine(String machineId) {
        return new ProductionScheduleRequested(Target.MACHINE, machineId);
    }

    static ProductionScheduleRequested forAllIdleMachines() {
        return new ProductionScheduleRequested(Target.ALL_IDLE_MACHINES, null);
    }

    enum Target {
        LOT,
        MACHINE,
        ALL_IDLE_MACHINES
    }
}
