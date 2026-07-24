package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.BomRequestDto;
import com.human.ev_relay_mes.Dto.Response.BomResponseDto;
import com.human.ev_relay_mes.Service.BomService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boms")
@RequiredArgsConstructor
public class BomController {

    private final BomService bomService;

    @GetMapping
    public List<BomResponseDto> getBoms() {
        return bomService.getBoms();
    }

    @GetMapping("/parent/{itemCode}")
    public List<BomResponseDto> getBom(@PathVariable String itemCode) {
        return bomService.getBom(itemCode);
    }

    @PostMapping
    public ResponseEntity<BomResponseDto> create(@Valid @RequestBody BomRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bomService.createBom(dto));
    }

    @PutMapping("/{id}")
    public BomResponseDto update(@PathVariable Long id, @Valid @RequestBody BomRequestDto dto) {
        return bomService.updateBom(id, dto);
    }

    @PatchMapping("/{id}/active")
    public BomResponseDto updateActive(@PathVariable Long id, @RequestParam boolean active) {
        return bomService.updateUseYn(id, active);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBom(@PathVariable Long id) {
        bomService.deleteBom(id);
        return ResponseEntity.noContent().build();
    }
}
