package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MaterialLotRequestDto {
    @NotBlank
    @Size(max = 50)
    private String materialLotNo;
    @NotBlank
    private String itemCode;
    @NotNull
    @Positive
    private Integer receivedQty;
    @NotNull
    private Long receivedBy;
}
