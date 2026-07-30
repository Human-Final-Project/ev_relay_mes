package com.human.ev_relay_mes.feature.material.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.masterdata.api.Bom;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
@RequiredArgsConstructor
class BomRequirementCalculator {

    private final MasterDataLookup masterDataLookup;

    Map<String, Integer> calculate(String parentItemCode, int productionQty) {
        if (productionQty <= 0) {
            throw new CustomException(ErrorCode.INVALID_LOT_QUANTITY);
        }

        Map<String, BigDecimal> quantityPerUnitByItem = new LinkedHashMap<>();
        explodeBom(
                parentItemCode,
                BigDecimal.ONE,
                quantityPerUnitByItem,
                new HashSet<>(),
                true);

        Map<String, Integer> requiredQtyByItem = new LinkedHashMap<>();
        quantityPerUnitByItem.forEach((itemCode, quantityPerUnit) ->
                requiredQtyByItem.put(
                        itemCode,
                        calculateRequiredQty(quantityPerUnit, productionQty)));
        return requiredQtyByItem;
    }

    private void explodeBom(
            String parentItemCode,
            BigDecimal multiplier,
            Map<String, BigDecimal> requiredByItem,
            Set<String> path,
            boolean root) {
        if (!path.add(parentItemCode)) {
            throw new CustomException(
                    ErrorCode.INVALID_BOM_ITEM_RELATION,
                    "BOM에 순환 참조가 존재합니다: " + parentItemCode);
        }

        List<Bom> boms = masterDataLookup.getActiveBom(parentItemCode);
        if (boms.isEmpty()) {
            path.remove(parentItemCode);
            if (root) {
                throw new CustomException(
                        ErrorCode.BOM_NOT_FOUND,
                        "생산 품목에 사용 가능한 BOM이 없습니다.");
            }
            requiredByItem.merge(parentItemCode, multiplier, BigDecimal::add);
            return;
        }

        for (Bom bom : boms) {
            Item child = bom.getChildItem();
            BigDecimal requiredMultiplier = multiplier.multiply(bom.getQuantity());
            if (child.getItemType() == Item.ItemType.RM) {
                requiredByItem.merge(
                        child.getItemCode(), requiredMultiplier, BigDecimal::add);
            } else {
                explodeBom(
                        child.getItemCode(),
                        requiredMultiplier,
                        requiredByItem,
                        path,
                        false);
            }
        }
        path.remove(parentItemCode);
    }

    private int calculateRequiredQty(BigDecimal quantityPerUnit, int productionQty) {
        try {
            return quantityPerUnit.multiply(BigDecimal.valueOf(productionQty))
                    .setScale(0, RoundingMode.CEILING)
                    .intValueExact();
        } catch (ArithmeticException exception) {
            throw new CustomException(ErrorCode.INVALID_BOM_QUANTITY);
        }
    }
}
