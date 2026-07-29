package com.human.ev_relay_mes.feature.machine.api;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 설비 알람 수신·조회·해제와 요약 집계에 사용하는 공개 계약입니다.
 */
public interface MachineAlarmOperations {

    MachineAlarmResponseDto createAlarm(MachineAlarmReceiveRequestDto dto);

    List<MachineAlarmResponseDto> search(MachineAlarmSearchRequestDto condition);

    MachineAlarmResponseDto clearAlarm(Long historyId, Long memberId);

    long countActiveAlarms();

    long countAlarmsOccurredBetween(LocalDateTime startAt, LocalDateTime endAt);
}
