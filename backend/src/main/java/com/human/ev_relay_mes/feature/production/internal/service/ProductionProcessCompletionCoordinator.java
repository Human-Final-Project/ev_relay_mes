package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.api.ProcessCodes;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.ProductionLog;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.production.internal.repository.ProductionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class ProductionProcessCompletionCoordinator {

    private final ProductionLogRepository productionLogRepository;
    private final MasterDataLookup masterDataLookup;
    private final WorkCommandOperations workCommandService;
    private final ProductionScheduleRequestService productionScheduleRequestService;
    private final WorkOrderContinuationRequestService workOrderContinuationRequestService;

    void complete(Lot lot, Machine machine, Process process,
                  int totalOkQty, LocalDateTime endedAt) {
        workCommandService.completeStartCommand(lot, process, machine);
        productionScheduleRequestService.requestMachine(machine.getMachineId());

        if (ProcessCodes.INITIAL_PARALLEL_PROCESSES.contains(process.getProcessCode())) {
            advanceAfterParallelProcesses(lot, endedAt);
            return;
        }

        Optional<Process> nextProcess = masterDataLookup.findNextProcess(process.getProcessOrder());
        if (nextProcess.isPresent()) {
            if (totalOkQty > 0) {
                lot.setCurrentProcess(nextProcess.get());
                productionScheduleRequestService.requestLot(lot.getLotNo());
            } else {
                finishScrappedLot(lot, endedAt);
            }
            return;
        }

        finishCompletedLot(lot, totalOkQty, endedAt);
    }

    private void advanceAfterParallelProcesses(Lot lot, LocalDateTime endedAt) {
        if (!isProcessCompleted(lot, ProcessCodes.WINDING)
                || !isProcessCompleted(lot, ProcessCodes.CONTACT_WELDING)) {
            return;
        }

        Process assembly = masterDataLookup.findProcess(ProcessCodes.ASSEMBLY)
                .orElseThrow(() -> new CustomException(
                        ErrorCode.PROCESS_NOT_FOUND,
                        "병렬 공정 합류 공정이 등록되어 있지 않습니다."));
        int assemblyInputQty = Math.min(
                processOkQty(lot, ProcessCodes.WINDING),
                processOkQty(lot, ProcessCodes.CONTACT_WELDING));
        if (assemblyInputQty > 0) {
            lot.setCurrentProcess(assembly);
            productionScheduleRequestService.requestLot(lot.getLotNo());
        } else {
            finishScrappedLot(lot, endedAt);
        }
    }

    private void finishCompletedLot(Lot lot, int totalOkQty, LocalDateTime endedAt) {
        lot.setOkQty(totalOkQty);
        lot.setNgQty(lot.getInputQty() - totalOkQty);
        lot.setStatus(Lot.Status.COMPLETED);
        lot.setCompletedAt(completionTime(endedAt));
        requestWorkOrderEvaluation(lot);
    }

    private void finishScrappedLot(Lot lot, LocalDateTime endedAt) {
        workCommandService.cancelActiveCommandsForLot(lot.getLotNo());
        lot.setOkQty(0);
        lot.setNgQty(lot.getInputQty());
        lot.setStatus(Lot.Status.SCRAPPED);
        lot.setCompletedAt(completionTime(endedAt));
        requestWorkOrderEvaluation(lot);
        productionScheduleRequestService.requestAllIdleMachines();
    }

    private void requestWorkOrderEvaluation(Lot lot) {
        lot.getWorkOrder().setStatus(WorkOrder.Status.RUNNING);
        workOrderContinuationRequestService.requestEvaluation(
                lot.getWorkOrder().getWorkOrderId());
    }

    private LocalDateTime completionTime(LocalDateTime endedAt) {
        return endedAt == null ? LocalDateTime.now() : endedAt;
    }

    private boolean isProcessCompleted(Lot lot, String processCode) {
        return productionLogs(lot, processCode).stream()
                .mapToInt(ProductionLog::getInputQty)
                .sum() == lot.getInputQty();
    }

    private int processOkQty(Lot lot, String processCode) {
        return productionLogs(lot, processCode).stream()
                .mapToInt(ProductionLog::getOkQty)
                .sum();
    }

    private List<ProductionLog> productionLogs(Lot lot, String processCode) {
        return productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), processCode);
    }
}
