package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.AlarmCode;

import java.util.List;

public interface AlarmCodeRepository extends JpaRepository<AlarmCode, String> {

    // 알람 코드 관리 화면이나 알람 등록용 선택 목록에 사용 가능한 코드를 표시할 때 사용한다.
    List<AlarmCode> findByUseYnOrderByAlarmCodeAsc(String useYn);

    // 설비 유형별로 발생 가능한 알람 코드만 선택 목록에 제공할 때 사용한다.
    List<AlarmCode> findByMachineTypeAndUseYnOrderByAlarmCodeAsc(String machineType, String useYn);

    // 신규 알람 코드 등록 전에 동일한 코드가 이미 사용 중인지 검사할 때 사용한다.
    boolean existsByAlarmCode(String alarmCode);
}
