package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class MachineAlarmReceiveRequestDto {

    @Size(max = 100)
    private String eventId;

    @NotBlank
    private String machineId;

    @NotBlank
    private String alarmCode;

    @NotBlank
    @Pattern(regexp = "(?i)INFO|WARN|ERROR")
    private String alarmLevel;

    private LocalDateTime occurredAt;
    private String message;
}
