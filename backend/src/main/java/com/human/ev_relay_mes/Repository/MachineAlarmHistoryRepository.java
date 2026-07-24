package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import com.human.ev_relay_mes.Entity.MachineAlarmHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MachineAlarmHistoryRepository extends JpaRepository<MachineAlarmHistory, Long> {

    Optional<MachineAlarmHistory> findByEventId(String eventId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from MachineAlarmHistory h where h.machineAlarmHistoryId = :id")
    java.util.Optional<MachineAlarmHistory> findByIdForUpdate(@Param("id") Long id);

    // 설비 상세 화면에서 특정 설비에 발생한 알람 이력을 표시할 때 사용한다.
    List<MachineAlarmHistory> findByMachine_MachineIdOrderByOccurredAtDesc(String machineId);

    // 동일한 알람 유형의 반복 발생 설비와 빈도를 분석할 때 사용한다.
    List<MachineAlarmHistory> findByAlarmCode_AlarmCodeOrderByOccurredAtDesc(String alarmCode);

    // 알람 관리 화면에서 심각도별 경고나 오류만 필터링할 때 사용한다.
    List<MachineAlarmHistory> findByAlarmLevelOrderByOccurredAtDesc(String alarmLevel);

    // 대시보드와 알람 화면에 현재 조치가 필요한 발생 중 알람을 표시할 때 사용한다.
    List<MachineAlarmHistory> findByClearedAtIsNullOrderByOccurredAtDesc();

    // 조치가 완료된 알람과 최근 해제 내역을 확인할 때 사용한다.
    List<MachineAlarmHistory> findByClearedAtIsNotNullOrderByClearedAtDesc();

    // 기간별 설비 장애 현황과 알람 발생 추이를 조회할 때 사용한다.
    List<MachineAlarmHistory> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime startAt, LocalDateTime endAt);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from MachineAlarmHistory h where h.machine.machineId = :machineId "
            + "and h.clearedAt is null order by h.occurredAt asc, h.machineAlarmHistoryId asc")
    List<MachineAlarmHistory> findActiveByMachineForUpdate(@Param("machineId") String machineId);

    List<MachineAlarmHistory> findByMachine_MachineIdAndClearedAtIsNullOrderByOccurredAtAsc(
            String machineId);

    boolean existsByMachine_MachineIdAndAlarmLevelIgnoreCaseAndClearedAtIsNullAndMachineAlarmHistoryIdNot(
            String machineId, String alarmLevel, Long historyId);
}
