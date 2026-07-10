package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.MaterialLotRequestDto;
import com.human.ev_relay_mes.Dto.Response.MaterialLotResponseDto;
import com.human.ev_relay_mes.Entity.MaterialLot;
import com.human.ev_relay_mes.Service.MaterialLotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/material-lot")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class MaterialLotController {

    private final MaterialLotService materialLotService;

    // 등록
    @PostMapping
    public ResponseEntity<MaterialLot> createMaterialLot(
            @RequestBody MaterialLotRequestDto dto){

        return ResponseEntity.ok(materialLotService.createMaterialLot(dto));
    }

    // 전체 조회
    @GetMapping
    public ResponseEntity<List<MaterialLotResponseDto>> getMaterialLots(){
        return ResponseEntity.ok(materialLotService.getMaterialLots());
    }

    // 상세 조회
    @GetMapping("/{id}")
    public ResponseEntity<MaterialLot> getMaterialLot(
            @PathVariable Long id){

        return ResponseEntity.ok(materialLotService.getMaterialLot(id));
    }

    // 수정
    @PutMapping
    public ResponseEntity<MaterialLot> updateMaterialLot(
            @RequestBody MaterialLot materialLot){

        return ResponseEntity.ok(materialLotService.updateMaterialLot(materialLot));
    }

    // 삭제
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteMaterialLot(
            @PathVariable Long id){
        materialLotService.deleteMaterialLot(id);

        return ResponseEntity.ok("삭제 완료");
    }

    // 상태 변경
    @PatchMapping("/{id}/status")
    public ResponseEntity<MaterialLot> updateStatus(
            @PathVariable Long id,
            @RequestParam MaterialLot.Status status){

        return ResponseEntity.ok(
                materialLotService.updateStatus(id, status)
        );
    }


}
