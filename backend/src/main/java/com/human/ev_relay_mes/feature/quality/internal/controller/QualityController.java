package com.human.ev_relay_mes.feature.quality.internal.controller;

import com.human.ev_relay_mes.feature.quality.api.DefectHistorySearchRequestDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionSearchRequestDto;
import com.human.ev_relay_mes.feature.quality.api.DefectHistoryResponseDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionResponseDto;
import com.human.ev_relay_mes.feature.quality.api.DefectOperations;
import com.human.ev_relay_mes.feature.quality.api.InspectionOperations;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {
    private final InspectionOperations inspectionOperations;
    private final DefectOperations defectOperations;

    @GetMapping("/inspections")
    public List<InspectionResponseDto> searchInspections(
            @Valid @ModelAttribute InspectionSearchRequestDto condition) {
        return inspectionOperations.search(condition);
    }

    @GetMapping("/defects")
    public List<DefectHistoryResponseDto> searchDefects(
            @Valid @ModelAttribute DefectHistorySearchRequestDto condition) {
        return defectOperations.search(condition);
    }
}
