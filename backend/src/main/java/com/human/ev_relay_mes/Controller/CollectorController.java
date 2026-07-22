package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.Dto.Request.UnitJudgmentReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.CollectorStatusRequestDto;
import com.human.ev_relay_mes.Dto.Response.DefectHistoryResponseDto;
import com.human.ev_relay_mes.Dto.Response.InspectionResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineAlarmResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.Dto.Response.ProductionLogResponseDto;
import com.human.ev_relay_mes.Dto.Response.WorkCommandResponseDto;
import com.human.ev_relay_mes.Dto.Response.InspectionUnitResultResponseDto;
import com.human.ev_relay_mes.Service.DefectService;
import com.human.ev_relay_mes.Service.InspectionService;
import com.human.ev_relay_mes.Service.MachineAlarmService;
import com.human.ev_relay_mes.Service.MachineService;
import com.human.ev_relay_mes.Service.ProductionService;
import com.human.ev_relay_mes.Service.WorkCommandService;
import com.human.ev_relay_mes.Service.SystemConnectionStatusService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final ProductionService productionService;
    private final InspectionService inspectionService;
    private final DefectService defectService;
    private final MachineService machineService;
    private final MachineAlarmService machineAlarmService;
    private final WorkCommandService workCommandService;
    private final SystemConnectionStatusService systemConnectionStatusService;

    @PostMapping("/status")
    public ResponseEntity<Void> receiveCollectorStatus(
            @Valid @RequestBody CollectorStatusRequestDto dto) {
        systemConnectionStatusService.report(dto);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/production-logs")
    public ResponseEntity<ProductionLogResponseDto> receiveProductionResult(
            @Valid @RequestBody ProductionResultReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionService.saveResult(dto));
    }

    @PostMapping("/inspections")
    public ResponseEntity<InspectionResponseDto> receiveInspection(
            @Valid @RequestBody InspectionResultReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.saveResult(dto));
    }

    @PostMapping("/judgments")
    public ResponseEntity<InspectionUnitResultResponseDto> receiveJudgment(
            @Valid @RequestBody UnitJudgmentReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionService.saveJudgment(dto));
    }

    @PostMapping("/defects")
    public ResponseEntity<DefectHistoryResponseDto> receiveDefect(
            @Valid @RequestBody DefectHistoryCreateRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(defectService.createDefect(dto));
    }

    @PostMapping("/machine-statuses")
    public ResponseEntity<MachineStatusHistoryResponseDto> receiveMachineStatus(
            @Valid @RequestBody MachineStatusReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineService.updateStatus(dto));
    }

    @PostMapping("/machine-alarms")
    public ResponseEntity<MachineAlarmResponseDto> receiveAlarm(
            @Valid @RequestBody MachineAlarmReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineAlarmService.createAlarm(dto));
    }

    @GetMapping("/commands/pending")
    public List<WorkCommandResponseDto> claimPendingCommands(
            @RequestParam(required = false) String machineId) {
        return workCommandService.claimPendingCommands(machineId);
    }

    @PostMapping("/commands/{commandId}/release")
    public WorkCommandResponseDto releaseCommand(
            @PathVariable Long commandId,
            @RequestParam String machineId) {
        return workCommandService.releaseDispatchedCommand(commandId, machineId);
    }

    @PostMapping("/command-acks")
    public WorkCommandResponseDto acknowledgeCommand(
            @Valid @RequestBody WorkCommandAckRequestDto dto) {
        return workCommandService.acknowledge(dto);
    }
}
