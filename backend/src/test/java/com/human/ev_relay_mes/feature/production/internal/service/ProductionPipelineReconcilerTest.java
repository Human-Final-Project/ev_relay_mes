package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Service.WorkCommandService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class ProductionPipelineReconcilerTest {

    @Mock private WorkCommandService workCommandService;
    @Mock private ProductionSchedulerService productionSchedulerService;

    @InjectMocks
    private ProductionPipelineReconciler reconciler;

    @Test
    void clearsTerminalLotCommandsBeforeReassigningIdleMachines() {
        reconciler.reconcile();

        InOrder order = inOrder(workCommandService, productionSchedulerService);
        order.verify(workCommandService).cancelActiveCommandsForTerminalLots();
        order.verify(productionSchedulerService).tryAssignAllIdleMachines();
    }
}
