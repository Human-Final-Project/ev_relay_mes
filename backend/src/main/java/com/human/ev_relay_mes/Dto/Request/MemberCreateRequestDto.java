package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberCreateRequestDto {

    @NotBlank
    private String loginId;

    @NotBlank
    private String password;

    @NotBlank
    private String memberName;

    @NotNull
    @Pattern(regexp = "(?i)ADMIN|OPERATOR")
    private String role;

    @Pattern(regexp = "(?i)ACTIVE|LOCKED|RETIRED")
    private String status;

    private String department;
    private String position;
}
