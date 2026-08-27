package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponseDto {

    private Long memberId;
    private String loginId;
    private String memberName;
    private String role;
    private String status;
}
