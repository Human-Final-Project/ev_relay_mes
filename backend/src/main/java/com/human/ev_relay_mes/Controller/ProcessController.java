package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.ProcessRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProcessResponseDto;
import jakarta.validation.Valid;
import com.human.ev_relay_mes.Service.ProcessService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
public class ProcessController {
    private final ProcessService processService;

    @GetMapping
    public List<ProcessResponseDto> getProcesses() {
        return processService.getProcesses();
    }

    @GetMapping("/{code}")
    public ProcessResponseDto getProcess(@PathVariable String code) {
        return processService.getProcess(code);
    }

    @PostMapping
    public ProcessResponseDto createProcess(@Valid @RequestBody ProcessRequestDto dto) {
        return processService.createProcess(dto);
    }

    @PutMapping("/{code}")
    public ProcessResponseDto updateProcess(@PathVariable String code,
            @Valid @RequestBody ProcessRequestDto dto) {
        return processService.updateProcess(code, dto);
    }

    @PatchMapping("/{code}/active")
    public ProcessResponseDto updateActive(@PathVariable String code, @RequestParam boolean active) {
        return processService.updateUseYn(code, active);
    }
}
