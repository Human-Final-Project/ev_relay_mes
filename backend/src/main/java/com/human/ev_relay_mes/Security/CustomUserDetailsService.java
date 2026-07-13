package com.human.ev_relay_mes.Security;

import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    // 로그인 요청의 ID로 회원을 조회해 Spring Security 인증 객체로 반환한다.
    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        Member member = memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UsernameNotFoundException("로그인 정보를 확인할 수 없습니다."));

        return CustomUserDetails.from(member);
    }
}
