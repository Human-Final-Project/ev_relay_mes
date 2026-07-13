package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.LoginRequestDto;
import com.human.ev_relay_mes.Dto.Response.LoginResponseDto;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LoginService {

    private final MemberRepository memberRepository;
    private final PasswordHashService passwordHashService;

    public LoginResponseDto login(LoginRequestDto dto) {
        Member member = memberRepository.findByLoginId(dto.getLoginId())
                .orElseThrow(() -> new CustomException(ErrorCode.LOGIN_FAILED));

        if (!passwordHashService.matches(dto.getPassword(), member.getPassword())) {
            throw new CustomException(ErrorCode.LOGIN_FAILED);
        }
        if (member.getStatus() == Member.Status.LOCKED) {
            throw new CustomException(ErrorCode.MEMBER_LOCKED);
        }
        if (member.getStatus() == Member.Status.RETIRED) {
            throw new CustomException(ErrorCode.MEMBER_RETIRED);
        }

        return LoginResponseDto.builder()
                .memberId(member.getMemberId())
                .loginId(member.getLoginId())
                .memberName(member.getMemberName())
                .role(member.getRole().name())
                .status(member.getStatus().name())
                .build();
    }
}
