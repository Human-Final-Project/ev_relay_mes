package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.api.ProductionData;
import com.human.ev_relay_mes.feature.production.api.WorkOrder;
import com.human.ev_relay_mes.feature.production.internal.repository.LotRepository;
import com.human.ev_relay_mes.feature.production.internal.repository.ProductionLogRepository;
import com.human.ev_relay_mes.feature.production.internal.repository.WorkOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductionDataService implements ProductionData {

    private final LotRepository lotRepository;
    private final WorkOrderRepository workOrderRepository;
    private final ProductionLogRepository productionLogRepository;

    @Override
    public Lot getRequiredLot(String lotNo) {
        return lotRepository.findByLotNo(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    @Override
    public Lot getRequiredLotForUpdate(String lotNo) {
        return lotRepository.findByLotNoForUpdate(lotNo)
                .orElseThrow(() -> new CustomException(ErrorCode.LOT_NOT_FOUND));
    }

    @Override
    public List<Lot> getAllLots() {
        return lotRepository.findAll();
    }

    @Override
    public List<WorkOrder> getAllWorkOrders() {
        return workOrderRepository.findAll();
    }

    @Override
    public int sumInputQuantity(String lotNo, String processCode) {
        return productionLogRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderByCreatedAtAsc(lotNo, processCode)
                .stream()
                .mapToInt(log -> log.getInputQty())
                .sum();
    }
}
