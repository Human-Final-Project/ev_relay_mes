package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.ProcessRequestDto;
import com.human.ev_relay_mes.Dto.Response.ProcessResponseDto;
import com.human.ev_relay_mes.Service.ProcessService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/processes")
@RequiredArgsConstructor
public class ProcessController {

    private final ProcessService processService;

    @PostMapping
    public ResponseEntity<ProcessResponseDto> createProcess(@Valid @RequestBody ProcessRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(processService.createProcess(dto));
    }

    @GetMapping
    public List<ProcessResponseDto> getProcesses() {
        return processService.getProcesses();
    }

    @GetMapping("/{code}")
    public ProcessResponseDto getProcess(@PathVariable String code) {
        return processService.getProcess(code);
    }

    @PutMapping("/{code}")
    public ProcessResponseDto updateProcess(
            @PathVariable String code, @Valid @RequestBody ProcessRequestDto dto) {
        return processService.updateProcess(code, dto);
    }

    @PatchMapping("/{code}/active")
    public ProcessResponseDto updateActive(@PathVariable String code, @RequestParam boolean active) {
        return processService.updateUseYn(code, active);
    }

    @DeleteMapping("/{code}")
    public ResponseEntity<Void> deleteProcess(@PathVariable String code) {
        processService.deleteProcess(code);
        return ResponseEntity.noContent().build();
    }
}
