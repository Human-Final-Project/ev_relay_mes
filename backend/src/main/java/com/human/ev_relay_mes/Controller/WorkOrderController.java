package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.LotCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.WorkOrderRequestDto;
import com.human.ev_relay_mes.Dto.Request.WorkOrderStatusRequestDto;
import com.human.ev_relay_mes.Dto.Response.LotResponseDto;
import com.human.ev_relay_mes.Dto.Response.WorkOrderResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import com.human.ev_relay_mes.Service.LotService;
import com.human.ev_relay_mes.Service.WorkOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/work-orders")
@RequiredArgsConstructor
public class WorkOrderController {

    private final WorkOrderService workOrderService;
    private final LotService lotService;

    @PostMapping
    public ResponseEntity<WorkOrderResponseDto> createWorkOrder(
            @Valid @RequestBody WorkOrderRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workOrderService.createWorkOrder(dto, userDetails.getMemberId()));
    }

    @GetMapping
    public List<WorkOrderResponseDto> getWorkOrders(
            @RequestParam(required = false) String status) {
        return workOrderService.getWorkOrders(status);
    }

    @GetMapping("/{id}")
    public WorkOrderResponseDto getWorkOrder(@PathVariable Long id) {
        return workOrderService.getWorkOrder(id);
    }

    @PutMapping("/{id}")
    public WorkOrderResponseDto updateWorkOrder(
            @PathVariable Long id, @Valid @RequestBody WorkOrderRequestDto dto) {
        return workOrderService.updateWorkOrder(id, dto);
    }

    @PostMapping("/{id}/release")
    public WorkOrderResponseDto releaseAndStart(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return workOrderService.releaseAndStart(id, userDetails.getMemberId());
    }

    @PatchMapping("/{id}/status")
    public WorkOrderResponseDto updateStatus(
            @PathVariable Long id, @Valid @RequestBody WorkOrderStatusRequestDto dto) {
        return workOrderService.updateStatus(id, dto);
    }

    @PostMapping("/{id}/lots")
    public ResponseEntity<LotResponseDto> createLot(
            @PathVariable Long id,
            @Valid @RequestBody LotCreateRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lotService.createLot(id, dto, userDetails.getMemberId()));
    }

    @PostMapping("/{id}/supplement")
    public ResponseEntity<LotResponseDto> createSupplementLot(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(lotService.createSupplementLot(id, userDetails.getMemberId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteWorkOrder(@PathVariable Long id) {
        workOrderService.deleteWorkOrder(id);
        return ResponseEntity.noContent().build();
    }
}
