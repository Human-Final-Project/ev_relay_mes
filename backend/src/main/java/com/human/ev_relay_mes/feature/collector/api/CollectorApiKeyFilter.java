package com.human.ev_relay_mes.feature.collector.api;

import jakarta.servlet.Filter;

/**
 * 보안 설정이 Collector 인증 구현 대신 의존하는 공개 필터 계약입니다.
 */
public interface CollectorApiKeyFilter extends Filter {

    String HEADER_NAME = "X-Collector-Key";
    String COLLECTOR_ROLE = "ROLE_COLLECTOR";
}
