package com.human.ev_relay_mes.Dto.Response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TemporaryPasswordResponseDto {

    private Long memberId;
    private String loginId;
    private String temporaryPassword;
}
