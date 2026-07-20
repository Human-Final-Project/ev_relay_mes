package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.InspectionStandardRequestDto;
import com.human.ev_relay_mes.Dto.Response.InspectionStandardResponseDto;
import com.human.ev_relay_mes.Service.InspectionStandardService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @PostMapping
    public ResponseEntity<InspectionStandardResponseDto> create(
            @Valid @RequestBody InspectionStandardRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(inspectionStandardService.create(dto));
    }

    @PutMapping("/{standardId}")
    public InspectionStandardResponseDto update(
            @PathVariable Long standardId,
            @Valid @RequestBody InspectionStandardRequestDto dto) {
        return inspectionStandardService.update(standardId, dto);
    }
}
