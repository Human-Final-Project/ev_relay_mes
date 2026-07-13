package com.human.ev_relay_mes.Dto.Request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class MemberUpdateRequestDto {

    private String role;
    private String status;
    private String department;
    private String position;
}
