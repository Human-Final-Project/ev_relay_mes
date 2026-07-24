package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkCommandAckRequestDto {

    @NotBlank
    private String machineId;

    @NotNull
    @Positive
    private Long commandId;

    @NotBlank
    @Pattern(regexp = "(?i)ACCEPTED|REJECTED")
    private String ackStatus;

    private String message;
}
