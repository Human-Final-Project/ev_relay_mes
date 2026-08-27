package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.WorkerRequestDto;
import com.human.ev_relay_mes.Dto.Response.WorkerResponseDto;
import com.human.ev_relay_mes.Service.WorkerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/workers")
@RequiredArgsConstructor
public class WorkerController {

    private final WorkerService workerService;

    @PostMapping
    public ResponseEntity<WorkerResponseDto> create(
            @Valid @RequestBody WorkerRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workerService.create(dto));
    }

    @GetMapping
    public List<WorkerResponseDto> getWorkers(
            @RequestParam(required = false) String status) {
        return workerService.getWorkers(status);
    }

    @GetMapping("/{id}")
    public WorkerResponseDto getWorker(@PathVariable Long id) {
        return workerService.getWorker(id);
    }

    @PutMapping("/{id}")
    public WorkerResponseDto update(
            @PathVariable Long id,
            @Valid @RequestBody WorkerRequestDto dto) {
        return workerService.update(id, dto);
    }

    @PatchMapping("/{id}/active")
    public WorkerResponseDto updateActive(
            @PathVariable Long id,
            @RequestParam boolean active) {
        return workerService.updateActive(id, active);
    }
}
