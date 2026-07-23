package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.MachineWorkerAssignmentRequestDto;
import com.human.ev_relay_mes.Dto.Response.MachineWorkerAssignmentResponseDto;
import com.human.ev_relay_mes.Service.MachineWorkerAssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/machines")
@RequiredArgsConstructor
public class MachineWorkerAssignmentController {

    private final MachineWorkerAssignmentService assignmentService;

    @GetMapping("/assignments")
    public List<MachineWorkerAssignmentResponseDto> getAllAssignments() {
        return assignmentService.getAllAssignments();
    }

    @GetMapping("/{machineId}/assignments")
    public List<MachineWorkerAssignmentResponseDto> getAssignments(
            @PathVariable String machineId) {
        return assignmentService.getAssignments(machineId);
    }

    @PutMapping("/{machineId}/responsible")
    public MachineWorkerAssignmentResponseDto assignResponsible(
            @PathVariable String machineId,
            @Valid @RequestBody MachineWorkerAssignmentRequestDto dto) {
        return assignmentService.assignResponsible(machineId, dto.getWorkerId());
    }

    @PostMapping("/{machineId}/workers")
    public ResponseEntity<MachineWorkerAssignmentResponseDto> addWorker(
            @PathVariable String machineId,
            @Valid @RequestBody MachineWorkerAssignmentRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.addWorker(machineId, dto.getWorkerId()));
    }

    @DeleteMapping("/{machineId}/assignments/{workerId}")
    public ResponseEntity<Void> removeAssignment(
            @PathVariable String machineId,
            @PathVariable Long workerId) {
        assignmentService.removeAssignment(machineId, workerId);
        return ResponseEntity.noContent().build();
    }
}
