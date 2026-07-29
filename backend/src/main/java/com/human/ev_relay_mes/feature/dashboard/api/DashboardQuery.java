package com.human.ev_relay_mes.feature.dashboard.api;

/**
 * 운영 대시보드 요약을 조회하는 공개 계약입니다.
 */
public interface DashboardQuery {

    DashboardSummary getSummary();
}
