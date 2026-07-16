package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.AlarmCodeRequestDto;
import com.human.ev_relay_mes.Dto.Response.AlarmCodeResponseDto;
import com.human.ev_relay_mes.Service.AlarmCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/alarm-codes")
@RequiredArgsConstructor
public class AlarmCodeController {

    private final AlarmCodeService alarmCodeService;

    @PostMapping
    public ResponseEntity<AlarmCodeResponseDto> create(@Valid @RequestBody AlarmCodeRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alarmCodeService.createAlarmCode(dto));
    }

    @GetMapping
    public List<AlarmCodeResponseDto> getAll(
            @RequestParam(required = false) String machineType,
            @RequestParam(defaultValue = "true") Boolean activeOnly) {
        return alarmCodeService.getAlarmCodes(machineType, activeOnly);
    }

    @GetMapping("/{code}")
    public AlarmCodeResponseDto getOne(@PathVariable String code) {
        return alarmCodeService.getAlarmCode(code);
    }

    @PutMapping("/{code}")
    public AlarmCodeResponseDto update(
            @PathVariable String code, @Valid @RequestBody AlarmCodeRequestDto dto) {
        return alarmCodeService.updateAlarmCode(code, dto);
    }

    @PatchMapping("/{code}/active")
    public AlarmCodeResponseDto updateActive(@PathVariable String code, @RequestParam boolean active) {
        return alarmCodeService.updateUseYn(code, active);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> delete(@PathVariable String code) {
        alarmCodeService.deleteAlarmCode(code);
        return ResponseEntity.noContent().build();
    }
}
