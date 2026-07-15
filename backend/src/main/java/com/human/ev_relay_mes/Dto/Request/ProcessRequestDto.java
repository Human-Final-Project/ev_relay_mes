package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProcessRequestDto {

    @NotBlank
    @Size(max = 30)
    private String processCode;

    @NotBlank
    @Size(max = 100)
    private String processName;

    @NotNull
    @Positive
    private Integer processOrder;

    @Size(max = 255)
    private String description;
}
