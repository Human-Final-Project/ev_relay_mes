package com.human.ev_relay_mes.feature.masterdata.internal.controller;

import com.human.ev_relay_mes.feature.masterdata.api.InspectionStandardRequestDto;
import com.human.ev_relay_mes.feature.masterdata.api.InspectionStandardResponseDto;
import com.human.ev_relay_mes.feature.masterdata.internal.service.InspectionStandardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/inspection-standards")
@RequiredArgsConstructor
public class InspectionStandardController {
    private final InspectionStandardService inspectionStandardService;

    @GetMapping
    public List<InspectionStandardResponseDto> getAll() {
        return inspectionStandardService.getAll();
    }

    @PatchMapping("/{standardId}/limits")
    public InspectionStandardResponseDto updateLimits(
            @PathVariable Long standardId,
            @Valid @RequestBody InspectionStandardRequestDto dto) {
        return inspectionStandardService.updateLimits(standardId, dto);
    }
}
