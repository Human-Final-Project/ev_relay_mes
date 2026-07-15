package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.WorkCommand;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class WorkCommandResponseDto {

    private Long commandId;
    private String commandType;
    private String machineId;
    private String processCode;
    private String lotNo;
    private Integer inputQty;
    private String status;
    private String ackMessage;
    private LocalDateTime createdAt;
    private LocalDateTime dispatchedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime completedAt;

    public static WorkCommandResponseDto fromEntity(WorkCommand command) {
        return WorkCommandResponseDto.builder()
                .commandId(command.getCommandId())
                .commandType(command.getCommandType().name())
                .machineId(command.getMachine().getMachineId())
                .processCode(command.getProcess().getProcessCode())
                .lotNo(command.getLot().getLotNo())
                .inputQty(command.getInputQty())
                .status(command.getStatus().name())
                .ackMessage(command.getAckMessage())
                .createdAt(command.getCreatedAt())
                .dispatchedAt(command.getDispatchedAt())
                .acknowledgedAt(command.getAcknowledgedAt())
                .completedAt(command.getCompletedAt())
                .build();
    }
}
