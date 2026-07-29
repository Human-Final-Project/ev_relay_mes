package com.human.ev_relay_mes.feature.collector.internal.controller;

import com.human.ev_relay_mes.feature.quality.api.DefectHistoryCreateRequestDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionResultReceiveRequestDto;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.feature.production.api.ProductionResultReceiveRequestDto;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandAckRequestDto;
import com.human.ev_relay_mes.feature.quality.api.UnitJudgmentReceiveRequestDto;
import com.human.ev_relay_mes.feature.quality.api.DefectHistoryResponseDto;
import com.human.ev_relay_mes.feature.quality.api.InspectionResponseDto;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmResponseDto;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.feature.production.api.ProductionLogResponseDto;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandResponseDto;
import com.human.ev_relay_mes.feature.collector.api.CollectorStatusOperations;
import com.human.ev_relay_mes.feature.collector.api.WorkCommandOperations;
import com.human.ev_relay_mes.feature.quality.api.InspectionUnitResultResponseDto;
import com.human.ev_relay_mes.feature.quality.api.DefectOperations;
import com.human.ev_relay_mes.feature.quality.api.InspectionOperations;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmOperations;
import com.human.ev_relay_mes.feature.machine.api.MachineMonitoring;
import com.human.ev_relay_mes.feature.production.api.ProductionOperations;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/collector")
@RequiredArgsConstructor
public class CollectorController {

    private final ProductionOperations productionOperations;
    private final InspectionOperations inspectionOperations;
    private final DefectOperations defectOperations;
    private final MachineMonitoring machineMonitoring;
    private final MachineAlarmOperations machineAlarmOperations;
    private final WorkCommandOperations workCommandOperations;
    private final CollectorStatusOperations collectorStatusOperations;

    @PostMapping("/status-heartbeat")
    public ResponseEntity<Void> receiveCollectorHeartbeat(
            @Valid @RequestBody CollectorHeartbeatRequest request) {
        collectorStatusOperations.receiveHeartbeat(
                request.connectedMachineIds(),
                request.totalCapacity());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/production-logs")
    public ResponseEntity<ProductionLogResponseDto> receiveProductionResult(
            @Valid @RequestBody ProductionResultReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productionOperations.saveResult(dto));
    }

    @PostMapping("/inspections")
    public ResponseEntity<InspectionResponseDto> receiveInspection(
            @Valid @RequestBody InspectionResultReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionOperations.saveResult(dto));
    }

    @PostMapping("/judgments")
    public ResponseEntity<InspectionUnitResultResponseDto> receiveJudgment(
            @Valid @RequestBody UnitJudgmentReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inspectionOperations.saveJudgment(dto));
    }

    @PostMapping("/defects")
    public ResponseEntity<DefectHistoryResponseDto> receiveDefect(
            @Valid @RequestBody DefectHistoryCreateRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(defectOperations.createDefect(dto));
    }

    @PostMapping("/machine-statuses")
    public ResponseEntity<MachineStatusHistoryResponseDto> receiveMachineStatus(
            @Valid @RequestBody MachineStatusReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineMonitoring.updateStatus(dto));
    }

    @PostMapping("/machine-alarms")
    public ResponseEntity<MachineAlarmResponseDto> receiveAlarm(
            @Valid @RequestBody MachineAlarmReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineAlarmOperations.createAlarm(dto));
    }

    @GetMapping("/commands/pending")
    public List<WorkCommandResponseDto> claimPendingCommands(
            @RequestParam(required = false) String machineId) {
        return workCommandOperations.claimPendingCommands(machineId);
    }

    @PostMapping("/commands/{commandId}/release")
    public WorkCommandResponseDto releaseCommand(
            @PathVariable Long commandId,
            @RequestParam String machineId) {
        return workCommandOperations.releaseDispatchedCommand(commandId, machineId);
    }

    @PostMapping("/command-acks")
    public WorkCommandResponseDto acknowledgeCommand(
            @Valid @RequestBody WorkCommandAckRequestDto dto) {
        return workCommandOperations.acknowledge(dto);
    }

    public record CollectorHeartbeatRequest(
            @NotNull List<String> connectedMachineIds,
            @NotNull @Positive Integer totalCapacity) {
    }
}
