package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PasswordChangeRequestDto {

    @NotBlank
    private String currentPassword;

    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;

    @NotBlank
    @Size(min = 8, max = 64)
    private String newPasswordConfirm;
}
