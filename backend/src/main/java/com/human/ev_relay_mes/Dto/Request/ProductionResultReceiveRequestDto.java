package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ProductionResultReceiveRequestDto {

    @NotBlank
    private String lotNo;

    @NotBlank
    private String machineId;

    @NotBlank
    private String processCode;

    @NotNull
    @PositiveOrZero
    private Integer inputQty;

    @NotNull
    @PositiveOrZero
    private Integer okQty;

    @NotNull
    @PositiveOrZero
    private Integer ngQty;

    @NotBlank
    private String status;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
