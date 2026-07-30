package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.feature.production.api.ProductionLog;
import com.human.ev_relay_mes.feature.production.api.ProductionLogResponseDto;
import org.springframework.stereotype.Component;

@Component
class ProductionLogResponseAssembler {

    ProductionLogResponseDto toResponse(ProductionLog log) {
        return ProductionLogResponseDto.builder()
                .productionLogId(log.getProductionLogId())
                .lotNo(log.getLot().getLotNo())
                .machineId(log.getMachine().getMachineId())
                .machineName(log.getMachine().getMachineName())
                .processCode(log.getProcess().getProcessCode())
                .processName(log.getProcess().getProcessName())
                .inputQty(log.getInputQty())
                .okQty(log.getOkQty())
                .ngQty(log.getNgQty())
                .status(log.getStatus())
                .startedAt(log.getStartedAt())
                .endedAt(log.getEndedAt())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
