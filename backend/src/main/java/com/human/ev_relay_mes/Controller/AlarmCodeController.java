package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Response.AlarmCodeResponseDto;
import com.human.ev_relay_mes.Service.AlarmCodeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alarm-codes")
@RequiredArgsConstructor
public class AlarmCodeController {
    private final AlarmCodeService alarmCodeService;

    @GetMapping
    public List<AlarmCodeResponseDto> getAll(@RequestParam(required = false) String machineType) {
        return alarmCodeService.getAlarmCodes(machineType);
    }

    @GetMapping("/{code}")
    public AlarmCodeResponseDto getOne(@PathVariable String code) {
        return alarmCodeService.getAlarmCode(code);
    }
}
