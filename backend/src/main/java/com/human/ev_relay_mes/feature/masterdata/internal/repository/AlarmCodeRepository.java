package com.human.ev_relay_mes.feature.masterdata.internal.repository;

import com.human.ev_relay_mes.feature.masterdata.api.AlarmCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlarmCodeRepository extends JpaRepository<AlarmCode, String> {
    boolean existsByAlarmCode(String alarmCode);
}
