package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.MaterialLotRequestDto;
import com.human.ev_relay_mes.Dto.Request.ProductionLogSearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.WorkOrderRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProductionLogResponseDto;
import com.human.ev_relay_mes.Dto.Response.WorkOrderResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import com.human.ev_relay_mes.Service.CollectorStatusService;
import com.human.ev_relay_mes.Service.DashboardService;
import com.human.ev_relay_mes.Service.MaterialLotService;
import com.human.ev_relay_mes.Service.ProductionService;
import com.human.ev_relay_mes.Service.WorkOrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mes")
@RequiredArgsConstructor
public class MesController {

    private final WorkOrderService workOrderService;
    private final MaterialLotService materialLotService;
    private final ProductionService productionService;
    private final DashboardService dashboardService;
    private final CollectorStatusService collectorStatusService;

    @GetMapping("/dashboard/summary")
    public DashboardService.DashboardSummary getDashboardSummary() {
        return dashboardService.getSummary();
    }

    @GetMapping("/collector-status")
    public CollectorStatusService.CollectorStatus getCollectorStatus() {
        return collectorStatusService.getStatus();
    }

    @PostMapping("/order")
    public ResponseEntity<OrderView> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        WorkOrderRequestDto dto = new WorkOrderRequestDto();
        dto.setItemCode(request.productCode());
        dto.setTargetQty(request.targetQty());
        WorkOrderResponseDto created = workOrderService.createWorkOrder(dto, userDetails.getMemberId());
        return ResponseEntity.status(HttpStatus.CREATED).body(toOrderView(created));
    }

    @GetMapping("/orders")
    public List<OrderView> getOrders() {
        return workOrderService.getWorkOrders(null).stream().map(this::toOrderView).toList();
    }

    @GetMapping("/material/stock")
    public List<MaterialStockView> getMaterialStock() {
        Map<String, MaterialStockAccumulator> stocks = new LinkedHashMap<>();
        materialLotService.getMaterialLots().stream()
                .filter(lot -> "AVAILABLE".equals(lot.getStatus()) || "HOLD".equals(lot.getStatus()))
                .forEach(lot -> stocks.computeIfAbsent(
                                lot.getItemCode(),
                                code -> new MaterialStockAccumulator(code, lot.getItemName()))
                        .add(lot.getCurrentQty()));
        return stocks.values().stream()
                .map(stock -> new MaterialStockView(
                        stock.code, stock.code, stock.name, stock.currentStock))
                .toList();
    }

    @PostMapping("/material/inbound")
    public ResponseEntity<Void> changeMaterialStock(
            @Valid @RequestBody MaterialChangeRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        if ("OUT".equalsIgnoreCase(request.type())) {
            materialLotService.issueMaterial(request.code(), request.amount());
        } else {
            MaterialLotRequestDto dto = new MaterialLotRequestDto();
            dto.setMaterialLotNo(generateMaterialLotNo());
            dto.setItemCode(request.code());
            dto.setReceivedQty(request.amount());
            dto.setReceivedBy(userDetails.getMemberId());
            materialLotService.createMaterialLot(dto);
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/production/recent-logs")
    public List<ProductionLogResponseDto> getRecentProductionLogs() {
        return productionService.search(new ProductionLogSearchRequestDto()).stream()
                .limit(10)
                .toList();
    }

    private OrderView toOrderView(WorkOrderResponseDto order) {
        return new OrderView(
                order.getWorkOrderId(),
                order.getOrderNo(),
                order.getOrderNo(),
                order.getItemCode(),
                order.getItemName(),
                order.getTargetQty(),
                order.getCompletedOkQty(),
                order.getRemainingQty(),
                order.getSupplementRequired(),
                toReactStatus(order.getStatus()),
                order.getCreatedAt());
    }

    private String toReactStatus(String status) {
        return switch (status) {
            case "CREATED", "RELEASED" -> "WAITING";
            default -> status;
        };
    }

    private String generateMaterialLotNo() {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "ML-" + date + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    public record CreateOrderRequest(
            @NotBlank String productCode,
            @NotNull @Positive Integer targetQty) {
    }

    public record MaterialChangeRequest(
            @NotBlank String code,
            String name,
            @NotNull @Positive Integer amount,
            @NotBlank @Pattern(regexp = "(?i)IN|OUT") String type,
            String reason) {
    }

    public record OrderView(
            Long id,
            String lotId,
            String orderNo,
            String productCode,
            String productName,
            Integer targetQty,
            Integer completedOkQty,
            Integer remainingQty,
            Boolean supplementRequired,
            String status,
            java.time.LocalDateTime createdAt) {
    }

    public record MaterialStockView(
            String id,
            String code,
            String name,
            Integer currentStock) {
    }

    private static final class MaterialStockAccumulator {
        private final String code;
        private final String name;
        private int currentStock;

        private MaterialStockAccumulator(String code, String name) {
            this.code = code;
            this.name = name;
        }

        private void add(int quantity) {
            currentStock += quantity;
        }
    }
}
