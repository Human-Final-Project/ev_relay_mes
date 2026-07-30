package com.human.ev_relay_mes.feature.workforce.api;

import com.human.ev_relay_mes.feature.workforce.api.Worker;
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
    private Long memberId;
    private String loginId;
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
                .memberId(worker.getMember() == null
                        ? null : worker.getMember().getMemberId())
                .loginId(worker.getMember() == null
                        ? null : worker.getMember().getLoginId())
                .createdAt(worker.getCreatedAt())
                .updatedAt(worker.getUpdatedAt())
                .build();
    }
}
