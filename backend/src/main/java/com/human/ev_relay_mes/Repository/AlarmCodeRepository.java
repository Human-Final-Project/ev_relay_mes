package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.AlarmCode;

import java.util.List;

public interface AlarmCodeRepository extends JpaRepository<AlarmCode, String> {

    List<AlarmCode> findByUseYnOrderByAlarmCodeAsc(String useYn);

    List<AlarmCode> findByMachineTypeAndUseYnOrderByAlarmCodeAsc(String machineType, String useYn);

    boolean existsByAlarmCode(String alarmCode);
}
