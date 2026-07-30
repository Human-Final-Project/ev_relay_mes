package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.feature.masterdata.api.MasterDataLookup;
import com.human.ev_relay_mes.feature.masterdata.api.Process;
import com.human.ev_relay_mes.feature.production.api.Lot;
import com.human.ev_relay_mes.feature.production.internal.repository.ProductionLogRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductionInputQuantityResolverTest {

    private final MasterDataLookup masterDataLookup = mock(MasterDataLookup.class);
    private final ProductionLogRepository productionLogRepository =
            mock(ProductionLogRepository.class);
    private final ProductionInputQuantityResolver resolver =
            new ProductionInputQuantityResolver(
                    masterDataLookup, productionLogRepository);

    @Test
    void productionResultUsesLotQuantityWhenPreviousProcessDoesNotExist() {
        Lot lot = Lot.builder().lotNo("LOT-001").inputQty(10).build();
        Process firstProcess = Process.builder()
                .processCode("OP10")
                .processOrder(1)
                .build();
        when(masterDataLookup.findPreviousProcess(1)).thenReturn(Optional.empty());

        assertThat(resolver.resolveForResult(lot, firstProcess)).isEqualTo(10);
    }

    @Test
    void schedulerKeepsZeroWhenPreviousProcessDoesNotExist() {
        Lot lot = Lot.builder().lotNo("LOT-001").inputQty(10).build();
        Process firstProcess = Process.builder()
                .processCode("OP10")
                .processOrder(1)
                .build();
        when(masterDataLookup.findPreviousProcess(1)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(lot, firstProcess)).isZero();
    }
}
