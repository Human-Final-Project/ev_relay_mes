package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.MachineWorkerAssignment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MachineWorkerAssignmentResponseDto {

    private Long assignmentId;
    private String machineId;
    private String machineName;
    private String processCode;
    private Long workerId;
    private String workerNo;
    private String workerName;
    private String department;
    private String position;
    private String assignmentRole;
    private LocalDateTime assignedAt;

    public static MachineWorkerAssignmentResponseDto fromEntity(
            MachineWorkerAssignment assignment) {
        return MachineWorkerAssignmentResponseDto.builder()
                .assignmentId(assignment.getAssignmentId())
                .machineId(assignment.getMachine().getMachineId())
                .machineName(assignment.getMachine().getMachineName())
                .processCode(assignment.getMachine().getProcess().getProcessCode())
                .workerId(assignment.getWorker().getWorkerId())
                .workerNo(assignment.getWorker().getWorkerNo())
                .workerName(assignment.getWorker().getWorkerName())
                .department(assignment.getWorker().getDepartment())
                .position(assignment.getWorker().getPosition())
                .assignmentRole(assignment.getAssignmentRole().name())
                .assignedAt(assignment.getAssignedAt())
                .build();
    }
}
