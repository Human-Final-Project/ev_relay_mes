package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.MachineAlarmSearchRequestDto;
import com.human.ev_relay_mes.Dto.Request.MachineRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineAlarmResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineResponseDto;
import com.human.ev_relay_mes.Dto.Response.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import com.human.ev_relay_mes.Service.MachineAlarmService;
import com.human.ev_relay_mes.Service.MachineService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/machines")
@RequiredArgsConstructor
public class MachineController {
    private final MachineService machineService;
    private final MachineAlarmService machineAlarmService;

    @GetMapping
    public List<MachineResponseDto> getMachines() { return machineService.getMachines(); }

    @GetMapping("/{id}")
    public MachineResponseDto getMachine(@PathVariable String id) { return machineService.getMachine(id); }

    @PostMapping
    public MachineResponseDto createMachine(@Valid @RequestBody MachineRequestDto dto) {
        return machineService.createMachine(dto);
    }

    @PutMapping("/{id}")
    public MachineResponseDto updateMachine(@PathVariable String id,
            @Valid @RequestBody MachineRequestDto dto) {
        return machineService.updateMachine(id, dto);
    }

    @PatchMapping("/{id}/active")
    public MachineResponseDto updateActive(@PathVariable String id, @RequestParam boolean active) {
        return machineService.updateUseYn(id, active);
    }

    @GetMapping("/{id}/status-history")
    public List<MachineStatusHistoryResponseDto> getStatusHistory(@PathVariable String id) {
        return machineService.getStatusHistory(id);
    }

    @GetMapping("/alarms")
    public List<MachineAlarmResponseDto> searchAlarms(@Valid @ModelAttribute MachineAlarmSearchRequestDto condition) {
        return machineAlarmService.search(condition);
    }

    @PatchMapping("/alarms/{id}/clear")
    public MachineAlarmResponseDto clearAlarm(@PathVariable Long id,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return machineAlarmService.clearAlarm(id, userDetails.getMemberId());
    }
}
