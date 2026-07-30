package com.human.ev_relay_mes.feature.material.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.material.api.LotMaterialUsage;
import com.human.ev_relay_mes.feature.material.api.MaterialLot;
import com.human.ev_relay_mes.feature.material.internal.repository.LotMaterialUsageRepository;
import com.human.ev_relay_mes.feature.material.internal.repository.MaterialLotRepository;
import com.human.ev_relay_mes.feature.production.api.Lot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
class MaterialStockAllocator {

    private final MaterialLotRepository materialLotRepository;
    private final LotMaterialUsageRepository lotMaterialUsageRepository;

    void validateAvailableStock(Map<String, Integer> requiredQtyByItem) {
        requiredQtyByItem.forEach((itemCode, requiredQty) -> {
            long availableQty = materialLotRepository.sumAvailableQty(
                    itemCode, MaterialLot.Status.AVAILABLE);
            if (availableQty < requiredQty) {
                throw insufficientMaterial(itemCode, requiredQty, availableQty);
            }
        });
    }

    void allocate(Map<String, Integer> requiredQtyByItem, Lot productionLot) {
        Map<String, List<MaterialLot>> availableLotsByItem = lockAvailableLots(
                requiredQtyByItem);
        requiredQtyByItem.forEach((itemCode, requiredQty) ->
                deductLots(availableLotsByItem.get(itemCode), requiredQty, productionLot));
    }

    void allocateSingleItem(String itemCode, int requiredQty) {
        List<MaterialLot> lots = materialLotRepository.findAvailableLotsForUpdate(
                itemCode, MaterialLot.Status.AVAILABLE);
        validateAvailableQuantity(itemCode, requiredQty, lots);
        deductLots(lots, requiredQty, null);
    }

    private Map<String, List<MaterialLot>> lockAvailableLots(
            Map<String, Integer> requiredQtyByItem) {
        Map<String, List<MaterialLot>> availableLotsByItem = new LinkedHashMap<>();
        requiredQtyByItem.forEach((itemCode, requiredQty) -> {
            List<MaterialLot> lots = materialLotRepository.findAvailableLotsForUpdate(
                    itemCode, MaterialLot.Status.AVAILABLE);
            validateAvailableQuantity(itemCode, requiredQty, lots);
            availableLotsByItem.put(itemCode, lots);
        });
        return availableLotsByItem;
    }

    private void validateAvailableQuantity(
            String itemCode, int requiredQty, List<MaterialLot> lots) {
        long availableQty = lots.stream().mapToLong(MaterialLot::getCurrentQty).sum();
        if (availableQty < requiredQty) {
            throw insufficientMaterial(itemCode, requiredQty, availableQty);
        }
    }

    private void deductLots(
            List<MaterialLot> lots, int requiredQty, Lot productionLot) {
        int remainingQty = requiredQty;
        for (MaterialLot materialLot : lots) {
            if (remainingQty == 0) {
                break;
            }
            int consumedQty = Math.min(materialLot.getCurrentQty(), remainingQty);
            materialLot.setCurrentQty(materialLot.getCurrentQty() - consumedQty);
            remainingQty -= consumedQty;
            recordUsage(productionLot, materialLot, consumedQty);
            if (materialLot.getCurrentQty() == 0) {
                materialLot.setStatus(MaterialLot.Status.USED);
            }
        }
    }

    private void recordUsage(
            Lot productionLot, MaterialLot materialLot, int consumedQty) {
        if (productionLot == null || consumedQty <= 0) {
            return;
        }
        lotMaterialUsageRepository.save(LotMaterialUsage.builder()
                .lot(productionLot)
                .materialLot(materialLot)
                .usedQty(consumedQty)
                .build());
    }

    private CustomException insufficientMaterial(
            String itemCode, int requiredQty, long availableQty) {
        return new CustomException(
                ErrorCode.INSUFFICIENT_MATERIAL_QUANTITY,
                itemCode + " 재고가 부족합니다. 필요: "
                        + requiredQty + ", 가용: " + availableQty);
    }
}
