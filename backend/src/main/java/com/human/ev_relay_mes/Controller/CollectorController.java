package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectHistoryResponseDto;
import com.human.ev_relay_mes.Dto.Response.InspectionResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineAlarmResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.Dto.Response.ProductionLogResponseDto;
import com.human.ev_relay_mes.Dto.Response.WorkCommandResponseDto;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Service.DefectService;
import com.human.ev_relay_mes.Service.InspectionService;
import com.human.ev_relay_mes.Service.MachineAlarmService;
import com.human.ev_relay_mes.Service.MachineService;
import com.human.ev_relay_mes.Service.ProductionService;
import com.human.ev_relay_mes.Service.WorkCommandService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class CollectorController {

    private static final String API_KEY_HEADER = "X-L2-API-Key";

    private final ProductionService productionService;
    private final InspectionService inspectionService;
    private final DefectService defectService;
    private final MachineService machineService;
    private final MachineAlarmService machineAlarmService;
    private final WorkCommandService workCommandService;

    @Value("${L2_API_KEY:}")
    private String configuredApiKey;

    @PostMapping("/production-logs")
    public ResponseEntity<ProductionLogResponseDto> receiveProductionResult(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody ProductionResultReceiveRequestDto dto) {
        validateApiKey(apiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(productionService.saveResult(dto));
    }

    @PostMapping("/inspections")
    public ResponseEntity<InspectionResponseDto> receiveInspection(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody InspectionResultReceiveRequestDto dto) {
        validateApiKey(apiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.saveResult(dto));
    }

    @PostMapping("/defects")
    public ResponseEntity<DefectHistoryResponseDto> receiveDefect(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody DefectHistoryCreateRequestDto dto) {
        validateApiKey(apiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(defectService.createDefect(dto));
    }

    @PostMapping("/machine-statuses")
    public ResponseEntity<MachineStatusHistoryResponseDto> receiveMachineStatus(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody MachineStatusReceiveRequestDto dto) {
        validateApiKey(apiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(machineService.updateStatus(dto));
    }

    @PostMapping("/machine-alarms")
    public ResponseEntity<MachineAlarmResponseDto> receiveAlarm(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody MachineAlarmReceiveRequestDto dto) {
        validateApiKey(apiKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(machineAlarmService.createAlarm(dto));
    }

    @GetMapping("/commands/pending")
    public List<WorkCommandResponseDto> claimPendingCommands(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey) {
        validateApiKey(apiKey);
        return workCommandService.claimPendingCommands();
    }

    @PostMapping("/command-acks")
    public WorkCommandResponseDto acknowledgeCommand(
            @RequestHeader(value = API_KEY_HEADER, required = false) String apiKey,
            @Valid @RequestBody WorkCommandAckRequestDto dto) {
        validateApiKey(apiKey);
        return workCommandService.acknowledge(dto);
    }

    private void validateApiKey(String apiKey) {
        if (configuredApiKey == null || configuredApiKey.isBlank() || apiKey == null
                || !MessageDigest.isEqual(
                        configuredApiKey.getBytes(StandardCharsets.UTF_8),
                        apiKey.getBytes(StandardCharsets.UTF_8))) {
            throw new CustomException(ErrorCode.UNAUTHORIZED, "L2 API 인증키가 올바르지 않습니다.");
        }
    }
}
