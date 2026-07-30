package com.human.ev_relay_mes.feature.material.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.masterdata.api.Bom;
import com.human.ev_relay_mes.feature.masterdata.api.Item;
import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BomRequirementCalculatorTest {

    @Mock
    private MasterDataLookup masterDataLookup;

    @Test
    void roundsFractionalMaterialRequirementUpToWholeQuantity() {
        Item finished = item("FG-001", Item.ItemType.FG);
        Item raw = item("RM-001", Item.ItemType.RM);
        when(masterDataLookup.getActiveBom("FG-001")).thenReturn(List.of(
                bom(finished, raw, "1.25")));
        BomRequirementCalculator calculator = new BomRequirementCalculator(masterDataLookup);

        var requirements = calculator.calculate("FG-001", 3);

        assertThat(requirements).containsExactly(
                org.assertj.core.api.Assertions.entry("RM-001", 4));
    }

    @Test
    void rejectsCircularBomReference() {
        Item finished = item("FG-001", Item.ItemType.FG);
        Item assembly = item("SA-001", Item.ItemType.SA);
        when(masterDataLookup.getActiveBom("FG-001")).thenReturn(List.of(
                bom(finished, assembly, "1")));
        when(masterDataLookup.getActiveBom("SA-001")).thenReturn(List.of(
                bom(assembly, finished, "1")));
        BomRequirementCalculator calculator = new BomRequirementCalculator(masterDataLookup);

        assertThatThrownBy(() -> calculator.calculate("FG-001", 1))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_BOM_ITEM_RELATION);
    }

    private Item item(String code, Item.ItemType type) {
        return Item.builder()
                .itemCode(code)
                .itemName(code)
                .itemType(type)
                .useYn("Y")
                .build();
    }

    private Bom bom(Item parent, Item child, String quantity) {
        return Bom.builder()
                .parentItem(parent)
                .childItem(child)
                .quantity(new BigDecimal(quantity))
                .useYn("Y")
                .build();
    }
}
