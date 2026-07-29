package com.human.ev_relay_mes.feature.workforce.api;

import java.util.Optional;

/**
 * 생산 기능이 설비 책임 작업자를 조회할 때 사용하는 공개 계약입니다.
 */
public interface WorkforceAssignmentLookup {

    Optional<MachineWorkerAssignment> findResponsible(String machineId);
}
