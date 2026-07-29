package com.human.ev_relay_mes.feature.production.api;

import com.human.ev_relay_mes.feature.machine.api.Machine;
import com.human.ev_relay_mes.feature.masterdata.api.Process;

import java.util.List;

public interface ProductionOperations {

    ProductionLogResponseDto saveResult(ProductionResultReceiveRequestDto dto);

    ProductionLogResponseDto completeEvaluatedProcess(
            Lot lot,
            Machine machine,
            Process process,
            int inputQty,
            int okQty,
            int ngQty);

    int expectedInputQtyFor(Lot lot, Process process);

    List<ProductionLogResponseDto> search(ProductionLogSearchRequestDto condition);

    ProductionLogResponseDto getProductionLog(Long id);
}
