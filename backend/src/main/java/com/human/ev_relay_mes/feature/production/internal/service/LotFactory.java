package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class LotFactory {

    private final LotRepository lotRepository;
    private final MasterDataLookup masterDataLookup;

    Lot create(
            WorkOrder workOrder,
            int inputQty,
            Lot.LotType lotType,
            int productionRound,
            Member creator) {
        Process firstProcess = masterDataLookup.getFirstProcess();
        return Lot.builder()
                .lotNo(generateLotNo())
                .workOrder(workOrder)
                .item(workOrder.getItem())
                .currentProcess(firstProcess)
                .lotType(lotType)
                .productionRound(productionRound)
                .inputQty(inputQty)
                .createdBy(creator)
                .build();
    }

    private String generateLotNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String lotNo;
        do {
            lotNo = "LOT-" + date + "-" + UUID.randomUUID().toString()
                    .substring(0, 8).toUpperCase();
        } while (lotRepository.existsByLotNo(lotNo));
        return lotNo;
    }
}
