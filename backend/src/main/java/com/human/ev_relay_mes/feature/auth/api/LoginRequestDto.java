package com.human.ev_relay_mes.feature.auth.api;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class LoginRequestDto {

    @NotBlank
    private String loginId;

    @NotBlank
    private String password;
}
