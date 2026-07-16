package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LotCreateRequestDto {

    @NotNull
    @Positive
    private Integer inputQty;
}
