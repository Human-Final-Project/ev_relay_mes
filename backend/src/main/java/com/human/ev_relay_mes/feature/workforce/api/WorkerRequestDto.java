package com.human.ev_relay_mes.feature.workforce.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class WorkerRequestDto {

    @NotBlank
    @Size(max = 30)
    @Pattern(
            regexp = "EVR\\d{8}",
            message = "사번은 EVR과 숫자 8자리 형식이어야 합니다. 예: EVR00000001")
    private String workerNo;

    @NotBlank
    @Size(max = 100)
    private String workerName;

    @Size(max = 50)
    private String department;

    @Size(max = 50)
    private String position;

    @Pattern(regexp = "(?i)ACTIVE|INACTIVE")
    private String status;
}
