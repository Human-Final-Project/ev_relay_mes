package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.BomRequestDto;
import com.human.ev_relay_mes.Dto.Response.BomResponseDto;
import com.human.ev_relay_mes.Entity.Bom;
import com.human.ev_relay_mes.Service.BomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/boms")
@RequiredArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
@Slf4j
public class BomController {

    private final BomService bomService;

    // 전체 BOM 조회
    @GetMapping
    public ResponseEntity<List<BomResponseDto>> getBoms(){
        return ResponseEntity.ok(bomService.getBoms());
    }

    // 부모 품목 기준 BOM 조회
    @GetMapping("/{itemCode}")
    public ResponseEntity<List<BomResponseDto>> getBom(@PathVariable String itemCode){
        return ResponseEntity.ok(bomService.getBom(itemCode));
    }

    // BOM 등록
    @PostMapping
    public ResponseEntity<Bom> create(@RequestBody BomRequestDto dto){
        return ResponseEntity.ok(bomService.createBom(dto));
    }

    // BOM 수정
    @PutMapping("/{id}")
    public ResponseEntity<Bom> update(@PathVariable Long id,
                                      @RequestBody BomRequestDto dto){
        return ResponseEntity.ok(bomService.updateBom(id,dto));
    }

    @PutMapping("/activate/{id}")
    public ResponseEntity<String> activate(@PathVariable Long id){
        bomService.activateBom(id);
        return ResponseEntity.ok("활성화 완료");
    }

    @PutMapping("/activate/{id}")
    public ResponseEntity<String> inactivate(@PathVariable Long id){
        bomService.inactivateBom(id);
        return ResponseEntity.ok("비활성화 완료");
    }

    // BOM 삭제
    @DeleteMapping("/{bomId}")
    public ResponseEntity<String> deleteBom(@PathVariable Long bomId){
        bomService.deleteBom(bomId);
        return ResponseEntity.ok("삭제 완료");
    }

}
