package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.masterdata.api.ProcessCodes;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.ProductionLog;
import com.human.ev_relay_mes.feature.production.internal.repository.ProductionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ProductionInputQuantityResolver {

    private final MasterDataLookup masterDataLookup;
    private final ProductionLogRepository productionLogRepository;

    int resolve(Lot lot, Process process) {
        if (ProcessCodes.ASSEMBLY.equals(process.getProcessCode())) {
            return Math.min(
                    processOkQty(lot, ProcessCodes.WINDING),
                    processOkQty(lot, ProcessCodes.CONTACT_WELDING));
        }
        return masterDataLookup.findPreviousProcess(process.getProcessOrder())
                .map(previous -> processOkQty(lot, previous.getProcessCode()))
                .orElse(0);
    }

    int resolveForResult(Lot lot, Process process) {
        if (ProcessCodes.INITIAL_PARALLEL_PROCESSES.contains(process.getProcessCode())) {
            return lot.getInputQty();
        }
        if (ProcessCodes.ASSEMBLY.equals(process.getProcessCode())) {
            return resolve(lot, process);
        }
        return masterDataLookup.findPreviousProcess(process.getProcessOrder())
                .map(previous -> processOkQty(lot, previous.getProcessCode()))
                .orElse(lot.getInputQty());
    }

    boolean isPreviousInputCompleted(Lot lot, Process process) {
        if (ProcessCodes.ASSEMBLY.equals(process.getProcessCode())) {
            return hasCompletedLog(lot, ProcessCodes.WINDING)
                    && hasCompletedLog(lot, ProcessCodes.CONTACT_WELDING);
        }
        return masterDataLookup.findPreviousProcess(process.getProcessOrder())
                .map(previous -> hasCompletedLog(lot, previous.getProcessCode()))
                .orElse(false);
    }

    private boolean hasCompletedLog(Lot lot, String processCode) {
        return productionLogs(lot, processCode).stream()
                .anyMatch(log -> "COMPLETED".equalsIgnoreCase(log.getStatus()));
    }

    private int processOkQty(Lot lot, String processCode) {
        return productionLogs(lot, processCode).stream()
                .mapToInt(ProductionLog::getOkQty)
                .sum();
    }

    private java.util.List<ProductionLog> productionLogs(
            Lot lot, String processCode) {
        return productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(
                        lot.getLotNo(), processCode);
    }
}
