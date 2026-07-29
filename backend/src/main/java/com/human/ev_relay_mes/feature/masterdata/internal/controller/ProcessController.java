package com.human.ev_relay_mes.feature.masterdata.internal.controller;

import com.human.ev_relay_mes.feature.masterdata.api.ProcessResponseDto;
import com.human.ev_relay_mes.feature.masterdata.internal.service.ProcessService;
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
}
