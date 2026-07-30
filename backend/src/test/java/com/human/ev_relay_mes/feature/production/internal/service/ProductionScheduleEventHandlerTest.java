package com.human.ev_relay_mes.feature.production.internal.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ProductionScheduleEventHandlerTest {

    @Mock
    private ProductionSchedulerService productionSchedulerService;

    private ProductionScheduleEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ProductionScheduleEventHandler(productionSchedulerService);
    }

    @Test
    void routesLotRequestToLotScheduler() {
        handler.handle(ProductionScheduleRequested.forLot("LOT-001"));

        verify(productionSchedulerService).tryScheduleLot("LOT-001");
    }

    @Test
    void routesMachineRequestToMachineScheduler() {
        handler.handle(ProductionScheduleRequested.forMachine("EQ-001"));

        verify(productionSchedulerService).tryAssignMachine("EQ-001");
    }

    @Test
    void routesAllIdleRequestToFullReassignment() {
        handler.handle(ProductionScheduleRequested.forAllIdleMachines());

        verify(productionSchedulerService).tryAssignAllIdleMachines();
    }
}
