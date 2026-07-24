package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.LotStatusRequestDto;
import com.human.ev_relay_mes.Dto.Response.LotResponseDto;
import com.human.ev_relay_mes.Dto.Response.LotMaterialUsageResponseDto;
import com.human.ev_relay_mes.Dto.Response.LotProcessResponsibleResponseDto;
import com.human.ev_relay_mes.Dto.Response.WorkCommandResponseDto;
import com.human.ev_relay_mes.Service.LotService;
import com.human.ev_relay_mes.Service.LotMaterialUsageService;
import com.human.ev_relay_mes.Service.LotProcessResponsibleService;
import com.human.ev_relay_mes.Service.WorkCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lots")
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;
    private final WorkCommandService workCommandService;
    private final LotProcessResponsibleService lotProcessResponsibleService;
    private final LotMaterialUsageService lotMaterialUsageService;

    @GetMapping
    public List<LotResponseDto> getLots(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long workOrderId) {
        return lotService.getLots(status, workOrderId);
    }

    @GetMapping("/{id}")
    public LotResponseDto getLot(@PathVariable Long id) {
        return lotService.getLot(id);
    }

    @GetMapping("/by-no/{lotNo}")
    public LotResponseDto getLotByNo(@PathVariable String lotNo) {
        return lotService.getLotByNo(lotNo);
    }

    @GetMapping("/by-no/{lotNo}/commands")
    public List<WorkCommandResponseDto> getCommands(@PathVariable String lotNo) {
        lotService.getLotByNo(lotNo);
        return workCommandService.getCommands(lotNo);
    }

    @GetMapping("/by-no/{lotNo}/responsibles")
    public List<LotProcessResponsibleResponseDto> getResponsibles(
            @PathVariable String lotNo) {
        lotService.getLotByNo(lotNo);
        return lotProcessResponsibleService.getByLotNo(lotNo);
    }

    @GetMapping("/by-no/{lotNo}/materials")
    public List<LotMaterialUsageResponseDto> getMaterialUsages(
            @PathVariable String lotNo) {
        lotService.getLotByNo(lotNo);
        return lotMaterialUsageService.getByLotNo(lotNo);
    }

    @PatchMapping("/{id}/status")
    public LotResponseDto updateStatus(
            @PathVariable Long id, @Valid @RequestBody LotStatusRequestDto dto) {
        return lotService.updateStatus(id, dto);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLot(@PathVariable Long id) {
        lotService.deleteLot(id);
        return ResponseEntity.noContent().build();
    }
}
