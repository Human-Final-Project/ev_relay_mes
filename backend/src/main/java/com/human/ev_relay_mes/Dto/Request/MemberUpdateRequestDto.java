package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberUpdateRequestDto {

    @Pattern(regexp = "(?i)ADMIN|MANAGER|OPERATOR|VIEWER")
    private String role;
    @Pattern(regexp = "(?i)ACTIVE|LOCKED|RETIRED")
    private String status;
    @Size(max = 50)
    private String department;
    @Size(max = 50)
    private String position;
}
