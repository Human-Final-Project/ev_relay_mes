package com.human.ev_relay_mes.feature.workforce.api;

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
