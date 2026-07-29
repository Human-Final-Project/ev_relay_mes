package com.human.ev_relay_mes.feature.production.api;

public interface ProductionSchedulingRequests {

    void requestLot(String lotNo);

    void requestMachine(String machineId);

    void requestAllIdleMachines();
}
