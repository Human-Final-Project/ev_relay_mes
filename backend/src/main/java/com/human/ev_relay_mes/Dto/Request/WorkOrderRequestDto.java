package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class WorkOrderRequestDto {

    @NotBlank
    private String itemCode;

    @NotNull
    @Positive
    private Integer targetQty;

    private LocalDateTime plannedStartAt;
    private LocalDateTime plannedEndAt;
}
