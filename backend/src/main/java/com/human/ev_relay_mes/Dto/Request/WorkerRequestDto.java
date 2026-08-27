package com.human.ev_relay_mes.Dto.Request;

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
