package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.MachineRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineAlarmReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineAlarmSearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineStatusReceiveRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineAlarmResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import com.human.ev_relay_mes.Service.MachineAlarmService;
import com.human.ev_relay_mes.Service.MachineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machines")
@RequiredArgsConstructor
public class MachineController {

    private final MachineService machineService;
    private final MachineAlarmService machineAlarmService;

    @PostMapping
    public ResponseEntity<MachineResponseDto> createMachine(@Valid @RequestBody MachineRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineService.createMachine(dto));
    }

    @GetMapping
    public List<MachineResponseDto> getMachines() {
        return machineService.getMachines();
    }

    @GetMapping("/{id}")
    public MachineResponseDto getMachine(@PathVariable String id) {
        return machineService.getMachine(id);
    }

    @PutMapping("/{id}")
    public MachineResponseDto updateMachine(
            @PathVariable String id, @Valid @RequestBody MachineRequestDto dto) {
        return machineService.updateMachine(id, dto);
    }

    @PatchMapping("/{id}/active")
    public MachineResponseDto updateActive(@PathVariable String id, @RequestParam boolean active) {
        return machineService.updateUseYn(id, active);
    }

    @PostMapping("/status")
    public MachineStatusHistoryResponseDto updateStatus(
            @Valid @RequestBody MachineStatusReceiveRequestDto dto) {
        return machineService.updateStatus(dto);
    }

    @GetMapping("/{id}/status-history")
    public List<MachineStatusHistoryResponseDto> getStatusHistory(@PathVariable String id) {
        return machineService.getStatusHistory(id);
    }

    @PostMapping("/alarms")
    public ResponseEntity<MachineAlarmResponseDto> createAlarm(
            @Valid @RequestBody MachineAlarmReceiveRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(machineAlarmService.createAlarm(dto));
    }

    @GetMapping("/alarms")
    public List<MachineAlarmResponseDto> searchAlarms(
            @Valid @ModelAttribute MachineAlarmSearchRequestDto condition) {
        return machineAlarmService.search(condition);
    }

    @PatchMapping("/alarms/{id}/clear")
    public MachineAlarmResponseDto clearAlarm(
            @PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return machineAlarmService.clearAlarm(id, userDetails.getMemberId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteMachine(@PathVariable String id) {
        machineService.deleteMachine(id);
        return ResponseEntity.noContent().build();
    }
}
