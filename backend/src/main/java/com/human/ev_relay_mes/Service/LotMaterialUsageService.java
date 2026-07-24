package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Response.LotMaterialUsageResponseDto;
import com.human.ev_relay_mes.Repository.LotMaterialUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotMaterialUsageService {

    private final LotMaterialUsageRepository lotMaterialUsageRepository;

    public List<LotMaterialUsageResponseDto> getByLotNo(String lotNo) {
        return lotMaterialUsageRepository
                .findByLotNo(lotNo)
                .stream()
                .map(LotMaterialUsageResponseDto::fromEntity)
                .toList();
    }
}
