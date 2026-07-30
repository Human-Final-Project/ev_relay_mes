package com.human.ev_relay_mes.feature.workforce.api;

import com.human.ev_relay_mes.feature.auth.api.Member;

/**
 * 로그인 사용자를 현장 작업자 마스터와 선택적으로 연결하는 공개 계약이다.
 */
public interface WorkforceMemberLinkOperations {

    void createOrLinkOperator(Member member);

    void synchronizeLinkedWorker(Member member);
}
