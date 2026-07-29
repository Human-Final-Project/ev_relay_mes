package com.human.ev_relay_mes.feature.masterdata.api;

import java.util.List;
import java.util.Optional;

/**
 * 다른 기능이 기준정보를 조회할 때 사용하는 공개 계약입니다.
 */
public interface MasterDataLookup {

    Item getRequiredItem(String itemCode);

    Process getRequiredProcess(String processCode);

    Optional<Process> findProcess(String processCode);

    Process getFirstProcess();

    Optional<Process> findNextProcess(Integer processOrder);

    Optional<Process> findPreviousProcess(Integer processOrder);

    List<Bom> getActiveBom(String parentItemCode);

    AlarmCode getRequiredAlarmCode(String alarmCode);

    DefectCode getRequiredDefectCode(String defectCode);
}
