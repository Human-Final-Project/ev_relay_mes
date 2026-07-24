package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Entity.Bom;
import com.human.ev_relay_mes.Entity.Item;
import com.human.ev_relay_mes.Entity.MaterialLot;
import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.LotMaterialUsage;
import com.human.ev_relay_mes.Repository.BomRepository;
import com.human.ev_relay_mes.Repository.ItemRepository;
import com.human.ev_relay_mes.Repository.MaterialLotRepository;
import com.human.ev_relay_mes.Repository.LotMaterialUsageRepository;
import com.human.ev_relay_mes.Repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaterialLotServiceTest {

    @Mock
    private MaterialLotRepository materialLotRepository;
    @Mock
    private ItemRepository itemRepository;
    @Mock
    private MemberRepository memberRepository;
    @Mock
    private BomRepository bomRepository;
    @Mock
    private LotMaterialUsageRepository lotMaterialUsageRepository;

    @InjectMocks
    private MaterialLotService materialLotService;

    @Test
    void consumesAvailableMaterialLotsInFifoOrder() {
        Item parent = item("FG-001", Item.ItemType.FG);
        Item child = item("RM-001", Item.ItemType.RM);
        Bom bom = Bom.builder()
                .parentItem(parent)
                .childItem(child)
                .quantity(new BigDecimal("2"))
                .useYn("Y")
                .build();
        MaterialLot first = materialLot(1L, child, 6);
        MaterialLot second = materialLot(2L, child, 20);

        when(bomRepository.findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc("FG-001", "Y"))
                .thenReturn(List.of(bom));
        when(materialLotRepository.findAvailableLotsForUpdate("RM-001", MaterialLot.Status.AVAILABLE))
                .thenReturn(List.of(first, second));

        materialLotService.consumeMaterials("FG-001", 5);

        assertThat(first.getCurrentQty()).isZero();
        assertThat(first.getStatus()).isEqualTo(MaterialLot.Status.USED);
        assertThat(second.getCurrentQty()).isEqualTo(16);
        assertThat(second.getStatus()).isEqualTo(MaterialLot.Status.AVAILABLE);
    }

    @Test
    void returnsFalseWithoutDeductionWhenAutomaticLotHasInsufficientMaterial() {
        Item parent = item("FG-001", Item.ItemType.FG);
        Item child = item("RM-001", Item.ItemType.RM);
        Bom bom = Bom.builder()
                .parentItem(parent)
                .childItem(child)
                .quantity(new BigDecimal("2"))
                .useYn("Y")
                .build();
        MaterialLot onlyLot = materialLot(1L, child, 5);

        when(bomRepository.findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc("FG-001", "Y"))
                .thenReturn(List.of(bom));
        when(materialLotRepository.findAvailableLotsForUpdate("RM-001", MaterialLot.Status.AVAILABLE))
                .thenReturn(List.of(onlyLot));

        boolean consumed = materialLotService.tryConsumeMaterials("FG-001", 5);

        assertThat(consumed).isFalse();
        assertThat(onlyLot.getCurrentQty()).isEqualTo(5);
        assertThat(onlyLot.getStatus()).isEqualTo(MaterialLot.Status.AVAILABLE);
    }


    @Test
    void recordsMaterialLotsUsedWhenProductionLotStarts() {
        Item finished = item("FG-001", Item.ItemType.FG);
        Item raw = item("RM-001", Item.ItemType.RM);
        Bom bom = Bom.builder()
                .parentItem(finished)
                .childItem(raw)
                .quantity(new BigDecimal("2"))
                .useYn("Y")
                .build();
        MaterialLot rawLot = materialLot(1L, raw, 20);
        Lot productionLot = Lot.builder()
                .lotId(100L)
                .lotNo("LOT-100")
                .item(finished)
                .inputQty(5)
                .build();

        when(bomRepository.findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc("FG-001", "Y"))
                .thenReturn(List.of(bom));
        when(materialLotRepository.findAvailableLotsForUpdate("RM-001", MaterialLot.Status.AVAILABLE))
                .thenReturn(List.of(rawLot));
        when(lotMaterialUsageRepository.save(any(LotMaterialUsage.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        boolean consumed = materialLotService.tryConsumeMaterials(productionLot);

        assertThat(consumed).isTrue();
        assertThat(rawLot.getCurrentQty()).isEqualTo(10);
        ArgumentCaptor<LotMaterialUsage> captor = ArgumentCaptor.forClass(LotMaterialUsage.class);
        verify(lotMaterialUsageRepository).save(captor.capture());
        assertThat(captor.getValue().getLot()).isSameAs(productionLot);
        assertThat(captor.getValue().getMaterialLot()).isSameAs(rawLot);
        assertThat(captor.getValue().getUsedQty()).isEqualTo(10);
    }

    @Test
    void explodesMultiLevelBomToRawMaterial() {
        Item finished = item("FG-001", Item.ItemType.FG);
        Item assembly = item("SA-001", Item.ItemType.SA);
        Item raw = item("RM-001", Item.ItemType.RM);
        Bom upper = Bom.builder()
                .parentItem(finished).childItem(assembly)
                .quantity(BigDecimal.ONE).useYn("Y").build();
        Bom lower = Bom.builder()
                .parentItem(assembly).childItem(raw)
                .quantity(new BigDecimal("2")).useYn("Y").build();
        MaterialLot rawLot = materialLot(1L, raw, 20);

        when(bomRepository.findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc("FG-001", "Y"))
                .thenReturn(List.of(upper));
        when(bomRepository.findByParentItem_ItemCodeAndUseYnOrderByChildItem_ItemCodeAsc("SA-001", "Y"))
                .thenReturn(List.of(lower));
        when(materialLotRepository.findAvailableLotsForUpdate("RM-001", MaterialLot.Status.AVAILABLE))
                .thenReturn(List.of(rawLot));

        materialLotService.consumeMaterials("FG-001", 5);

        assertThat(rawLot.getCurrentQty()).isEqualTo(10);
    }

    private Item item(String code, Item.ItemType type) {
        return Item.builder().itemCode(code).itemName(code).itemType(type).useYn("Y").build();
    }

    private MaterialLot materialLot(Long id, Item item, int currentQty) {
        return MaterialLot.builder()
                .materialLotId(id)
                .materialLotNo("ML-" + id)
                .item(item)
                .receivedQty(currentQty)
                .currentQty(currentQty)
                .status(MaterialLot.Status.AVAILABLE)
                .build();
    }
}
