package com.human.ev_relay_mes.Service;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 생산 상태 변경 트랜잭션과 설비 배정 트랜잭션을 분리하기 위한 요청 발행기다.
 * 현재 LOT 잠금을 해제한 뒤 스케줄러가 다른 LOT을 잠그므로 교착 위험을 줄인다.
 */
@Service
@RequiredArgsConstructor
public class ProductionScheduleRequestService {

    private final ApplicationEventPublisher eventPublisher;

    public void requestLot(String lotNo) {
        eventPublisher.publishEvent(new ScheduleRequested(ScheduleTarget.LOT, lotNo));
    }

    public void requestMachine(String machineId) {
        eventPublisher.publishEvent(new ScheduleRequested(ScheduleTarget.MACHINE, machineId));
    }

    public void requestAllIdleMachines() {
        eventPublisher.publishEvent(new ScheduleRequested(ScheduleTarget.ALL_IDLE_MACHINES, null));
    }

    public enum ScheduleTarget {
        LOT,
        MACHINE,
        ALL_IDLE_MACHINES
    }

    public record ScheduleRequested(ScheduleTarget target, String key) {
    }
}
