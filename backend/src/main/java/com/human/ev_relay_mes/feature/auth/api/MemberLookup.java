package com.human.ev_relay_mes.feature.auth.api;

/**
 * 다른 기능이 로그인 ID로 사용자를 조회할 때 사용하는 인증 모듈의 공개 계약입니다.
 */
public interface MemberLookup {

    Member getRequiredById(Long memberId);

    Member getRequiredByLoginId(String loginId);
}
