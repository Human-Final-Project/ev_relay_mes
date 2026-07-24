package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.LoginRequestDto;
import com.human.ev_relay_mes.Dto.Response.LoginResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final AuthenticationManager authenticationManager;

    // 로그인 ID와 비밀번호를 Spring Security 인증 절차에 전달한다.
    public Authentication authenticate(LoginRequestDto dto) {
        UsernamePasswordAuthenticationToken authenticationRequest =
                UsernamePasswordAuthenticationToken.unauthenticated(dto.getLoginId(), dto.getPassword());
        return authenticationManager.authenticate(authenticationRequest);
    }

    // 인증된 사용자 정보를 로그인 및 내 정보 API 응답으로 변환한다.
    public LoginResponseDto toResponse(CustomUserDetails userDetails) {
        return LoginResponseDto.builder()
                .memberId(userDetails.getMemberId())
                .loginId(userDetails.getLoginId())
                .memberName(userDetails.getMemberName())
                .role(userDetails.getRole().name())
                .status(userDetails.getStatus().name())
                .build();
    }
}
