package com.human.ev_relay_mes.feature.masterdata.api;

import com.human.ev_relay_mes.Entity.Lot;
import com.human.ev_relay_mes.Entity.LotInspectionStandardSnapshot;

import java.util.List;

/**
 * 품질 기능이 검사기준과 LOT별 기준 스냅샷을 사용할 때 의존하는 공개 계약입니다.
 */
public interface InspectionStandardOperations {

    List<LotInspectionStandardSnapshot> captureStandardsIfAbsent(Lot lot, Process process);

    LotInspectionStandardSnapshot resolveSnapshot(
            Lot lot, Process process, String inspectionItem);

    long snapshotCount(Lot lot, Process process);

    boolean supportsMeasurements(String processCode);
}
