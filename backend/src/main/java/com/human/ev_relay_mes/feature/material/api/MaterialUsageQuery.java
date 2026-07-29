package com.human.ev_relay_mes.feature.material.api;

import java.util.List;

public interface MaterialUsageQuery {

    List<LotMaterialUsageResponseDto> getByLotNo(String lotNo);
}
