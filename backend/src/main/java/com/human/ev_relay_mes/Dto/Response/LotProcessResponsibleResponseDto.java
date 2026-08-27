package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.LotProcessResponsible;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class LotProcessResponsibleResponseDto {

    private String lotNo;
    private String processCode;
    private String processName;
    private String machineId;
    private String machineName;
    private Long workerId;
    private String workerNo;
    private String workerName;
    private LocalDateTime capturedAt;

    public static LotProcessResponsibleResponseDto fromEntity(
            LotProcessResponsible responsible) {
        return LotProcessResponsibleResponseDto.builder()
                .lotNo(responsible.getLot().getLotNo())
                .processCode(responsible.getProcess().getProcessCode())
                .processName(responsible.getProcess().getProcessName())
                .machineId(responsible.getMachine().getMachineId())
                .machineName(responsible.getMachine().getMachineName())
                .workerId(responsible.getWorker().getWorkerId())
                .workerNo(responsible.getWorker().getWorkerNo())
                .workerName(responsible.getWorker().getWorkerName())
                .capturedAt(responsible.getCapturedAt())
                .build();
    }
}
