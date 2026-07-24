package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.MaterialLotRequestDto;
import com.human.ev_relay_mes.Dto.Response.MaterialLotResponseDto;
import com.human.ev_relay_mes.Entity.MaterialLot;
import com.human.ev_relay_mes.Service.MaterialLotService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/material-lots")
@RequiredArgsConstructor
public class MaterialLotController {

    private final MaterialLotService materialLotService;

    @PostMapping
    public ResponseEntity<MaterialLotResponseDto> createMaterialLot(
            @Valid @RequestBody MaterialLotRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(materialLotService.createMaterialLot(dto));
    }

    @GetMapping
    public List<MaterialLotResponseDto> getMaterialLots() {
        return materialLotService.getMaterialLots();
    }

    @GetMapping("/{id}")
    public MaterialLotResponseDto getMaterialLot(@PathVariable Long id) {
        return materialLotService.getMaterialLot(id);
    }

    @PatchMapping("/{id}/status")
    public MaterialLotResponseDto updateStatus(
            @PathVariable Long id, @RequestParam MaterialLot.Status status) {
        return materialLotService.updateStatus(id, status);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMaterialLot(@PathVariable Long id) {
        materialLotService.deleteMaterialLot(id);
        return ResponseEntity.noContent().build();
    }
}
