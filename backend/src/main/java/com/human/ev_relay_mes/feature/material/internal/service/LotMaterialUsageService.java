package com.human.ev_relay_mes.feature.material.internal.service;

import com.human.ev_relay_mes.feature.material.api.LotMaterialUsageResponseDto;
import com.human.ev_relay_mes.feature.material.api.MaterialUsageQuery;
import com.human.ev_relay_mes.feature.material.internal.repository.LotMaterialUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotMaterialUsageService implements MaterialUsageQuery {

    private final LotMaterialUsageRepository lotMaterialUsageRepository;

    public List<LotMaterialUsageResponseDto> getByLotNo(String lotNo) {
        return lotMaterialUsageRepository
                .findByLotNo(lotNo)
                .stream()
                .map(LotMaterialUsageResponseDto::fromEntity)
                .toList();
    }
}
