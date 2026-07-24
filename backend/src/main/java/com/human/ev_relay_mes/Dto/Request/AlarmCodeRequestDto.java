package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AlarmCodeRequestDto {

    @NotBlank
    @Size(max = 50)
    private String alarmCode;

    @NotBlank
    @Size(max = 100)
    private String alarmName;

    @NotBlank
    @Size(max = 30)
    private String machineType;

    @Size(max = 255)
    private String description;
}
