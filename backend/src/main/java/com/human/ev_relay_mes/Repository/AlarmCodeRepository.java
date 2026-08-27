package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.AlarmCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmCodeRepository extends JpaRepository<AlarmCode, String> {
    boolean existsByAlarmCode(String alarmCode);
}
