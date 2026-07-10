package com.human.ev_relay_mes.repository;

import com.human.ev_relay_mes.entity.MachineAlarmHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MachineAlarmHistoryRepository extends JpaRepository<MachineAlarmHistory, Long> {

    List<MachineAlarmHistory> findByMachine_MachineIdOrderByOccurredAtDesc(String machineId);

    List<MachineAlarmHistory> findByAlarmCode_AlarmCodeOrderByOccurredAtDesc(String alarmCode);

    List<MachineAlarmHistory> findByAlarmLevelOrderByOccurredAtDesc(String alarmLevel);

    List<MachineAlarmHistory> findByClearedAtIsNullOrderByOccurredAtDesc();

    List<MachineAlarmHistory> findByClearedAtIsNotNullOrderByClearedAtDesc();

    List<MachineAlarmHistory> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime startAt, LocalDateTime endAt);
}
