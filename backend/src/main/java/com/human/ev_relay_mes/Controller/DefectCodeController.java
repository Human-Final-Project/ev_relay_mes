package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.DefectCodeRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectCodeResponseDto;
import com.human.ev_relay_mes.Service.DefectCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/defect-codes")
@RequiredArgsConstructor
public class DefectCodeController {

    private final DefectCodeService defectCodeService;

    @PostMapping
    public ResponseEntity<DefectCodeResponseDto> create(@Valid @RequestBody DefectCodeRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(defectCodeService.createDefectCode(dto));
    }

    @GetMapping
    public List<DefectCodeResponseDto> getAll(
            @RequestParam(required = false) String processCode,
            @RequestParam(defaultValue = "true") Boolean activeOnly) {
        return defectCodeService.getDefectCodes(processCode, activeOnly);
    }

    @GetMapping("/{code}")
    public DefectCodeResponseDto getOne(@PathVariable String code) {
        return defectCodeService.getDefectCode(code);
    }

    @PutMapping("/{code}")
    public DefectCodeResponseDto update(
            @PathVariable String code, @Valid @RequestBody DefectCodeRequestDto dto) {
        return defectCodeService.updateDefectCode(code, dto);
    }

    @PatchMapping("/{code}/active")
    public DefectCodeResponseDto updateActive(@PathVariable String code, @RequestParam boolean active) {
        return defectCodeService.updateUseYn(code, active);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        defectCodeService.deleteDefectCode(code);
        return ResponseEntity.noContent().build();
    }
}
