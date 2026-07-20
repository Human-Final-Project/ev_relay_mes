package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.DefectHistorySearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionSearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectHistoryResponseDto;
import com.human.ev_relay_mes.Dto.Response.InspectionResponseDto;
import com.human.ev_relay_mes.Service.DefectService;
import com.human.ev_relay_mes.Service.InspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {
    private final InspectionService inspectionService;
    private final DefectService defectService;

    @GetMapping("/inspections")
    public List<InspectionResponseDto> searchInspections(
            @Valid @ModelAttribute InspectionSearchRequestDto condition) {
        return inspectionService.search(condition);
    }

    @GetMapping("/defects")
    public List<DefectHistoryResponseDto> searchDefects(
            @Valid @ModelAttribute DefectHistorySearchRequestDto condition) {
        return defectService.search(condition);
    }
}
