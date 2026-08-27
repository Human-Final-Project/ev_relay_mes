package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LotStatusRequestDto {

    @NotBlank
    @Pattern(regexp = "(?i)WAITING|RUNNING|COMPLETED|HOLD|SCRAPPED")
    private String status;
}
