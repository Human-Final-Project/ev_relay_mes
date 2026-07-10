package com.human.ev_relay_mes.repository;

import com.human.ev_relay_mes.entity.AlarmCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AlarmCodeRepository extends JpaRepository<AlarmCode, String> {

    List<AlarmCode> findByUseYnOrderByAlarmCodeAsc(String useYn);

    List<AlarmCode> findByMachineTypeAndUseYnOrderByAlarmCodeAsc(String machineType, String useYn);

    boolean existsByAlarmCode(String alarmCode);
}
