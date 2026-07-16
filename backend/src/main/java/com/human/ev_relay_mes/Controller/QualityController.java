package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.DefectHistorySearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionSearchRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectHistoryResponseDto;
import com.human.ev_relay_mes.Dto.Response.InspectionResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import com.human.ev_relay_mes.Service.DefectService;
import com.human.ev_relay_mes.Service.InspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/quality")
@RequiredArgsConstructor
public class QualityController {

    private final InspectionService inspectionService;
    private final DefectService defectService;

    @PostMapping("/inspections")
    public ResponseEntity<InspectionResponseDto> saveInspection(
            @Valid @RequestBody InspectionResultReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.saveResult(dto));
    }

    @GetMapping("/inspections")
    public List<InspectionResponseDto> searchInspections(
            @Valid @ModelAttribute InspectionSearchRequestDto condition) {
        return inspectionService.search(condition);
    }

    @PostMapping("/defects")
    public ResponseEntity<DefectHistoryResponseDto> createDefect(
            @Valid @RequestBody DefectHistoryCreateRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(defectService.createDefect(dto));
    }

    @GetMapping("/defects")
    public List<DefectHistoryResponseDto> searchDefects(
            @Valid @ModelAttribute DefectHistorySearchRequestDto condition) {
        return defectService.search(condition);
    }

    @PatchMapping("/defects/{id}/confirm")
    public DefectHistoryResponseDto confirmDefect(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return defectService.confirmDefect(id, userDetails.getMemberId());
    }
}
