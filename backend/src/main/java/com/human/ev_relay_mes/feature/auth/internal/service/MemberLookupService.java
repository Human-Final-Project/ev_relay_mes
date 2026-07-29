package com.human.ev_relay_mes.feature.auth.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberLookup;
import com.human.ev_relay_mes.feature.auth.internal.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberLookupService implements MemberLookup {

    private final MemberRepository memberRepository;

    @Override
    public Member getRequiredById(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    @Override
    public Member getRequiredByLoginId(String loginId) {
        return memberRepository.findByLoginId(loginId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }
}
