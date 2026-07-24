package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.Worker;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkerResponseDto {

    private Long workerId;
    private String workerNo;
    private String workerName;
    private String department;
    private String position;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static WorkerResponseDto fromEntity(Worker worker) {
        return WorkerResponseDto.builder()
                .workerId(worker.getWorkerId())
                .workerNo(worker.getWorkerNo())
                .workerName(worker.getWorkerName())
                .department(worker.getDepartment())
                .position(worker.getPosition())
                .status(worker.getStatus().name())
                .createdAt(worker.getCreatedAt())
                .updatedAt(worker.getUpdatedAt())
                .build();
    }
}
