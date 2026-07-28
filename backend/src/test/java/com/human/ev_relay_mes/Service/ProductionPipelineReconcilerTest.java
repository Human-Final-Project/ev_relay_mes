package com.human.ev_relay_mes.Service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductionPipelineReconcilerTest {

    @Mock private ProductionSchedulerService productionSchedulerService;
    @Mock private WorkCommandService workCommandService;

    @Test
    void cleansTerminalLotCommandsBeforeReassigningIdleMachines() {
        ProductionPipelineReconciler reconciler = new ProductionPipelineReconciler(
                productionSchedulerService, workCommandService);
        when(workCommandService.cancelActiveCommandsForTerminalLots()).thenReturn(2);
        when(productionSchedulerService.tryAssignAllIdleMachines()).thenReturn(1);

        reconciler.reconcile();

        InOrder order = inOrder(workCommandService, productionSchedulerService);
        order.verify(workCommandService).cancelActiveCommandsForTerminalLots();
        order.verify(productionSchedulerService).tryAssignAllIdleMachines();
    }
}
