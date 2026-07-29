package com.human.ev_relay_mes.feature.machine.internal.controller;

import com.human.ev_relay_mes.feature.machine.api.MachineAlarmSearchRequestDto;
import com.human.ev_relay_mes.feature.machine.api.MachineAlarmResponseDto;
import com.human.ev_relay_mes.feature.machine.api.MachineResponseDto;
import com.human.ev_relay_mes.feature.machine.api.MachineStatusHistoryResponseDto;
import com.human.ev_relay_mes.feature.auth.api.CustomUserDetails;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.machine.internal.service.MachineAlarmService;
import com.human.ev_relay_mes.feature.machine.internal.service.MachineService;
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
        if (userDetails == null) {
            throw new CustomException(ErrorCode.UNAUTHORIZED);
        }
        return machineAlarmService.clearAlarm(id, userDetails.getMemberId());
    }
}
