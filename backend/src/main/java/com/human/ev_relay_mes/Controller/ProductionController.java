package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.ProductionLogSearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProductionLogResponseDto;
import com.human.ev_relay_mes.Service.ProductionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/production-logs")
@RequiredArgsConstructor
public class ProductionController {

    private final ProductionService productionService;

    @PostMapping
    public ResponseEntity<ProductionLogResponseDto> saveResult(
            @Valid @RequestBody ProductionResultReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionService.saveResult(dto));
    }

    @GetMapping
    public List<ProductionLogResponseDto> search(
            @Valid @ModelAttribute ProductionLogSearchRequestDto condition) {
        return productionService.search(condition);
    }

    @GetMapping("/{id}")
    public ProductionLogResponseDto getProductionLog(@PathVariable Long id) {
        return productionService.getProductionLog(id);
    }
}
