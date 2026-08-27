package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MachineWorkerAssignmentRequestDto {

    @NotNull
    private Long workerId;
}
