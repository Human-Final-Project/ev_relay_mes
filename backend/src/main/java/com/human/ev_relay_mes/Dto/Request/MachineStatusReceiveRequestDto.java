package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MachineStatusReceiveRequestDto {

    @NotBlank
    private String machineId;

    @NotBlank
    private String status;

    private String lotNo;
    private String processCode;
    private String message;
}
