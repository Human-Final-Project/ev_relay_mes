package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MachineRequestDto {

    @NotBlank
    @Size(max = 50)
    private String machineId;

    @NotBlank
    @Size(max = 100)
    private String machineName;

    @NotBlank
    @Size(max = 30)
    private String machineType;

    @NotBlank
    private String processCode;
}
