package com.human.ev_relay_mes.feature.production.api;

import java.util.List;
import java.util.Optional;

public interface LotInspectionSnapshotStore {

    Optional<LotInspectionStandardSnapshot> findSnapshot(
            String lotNo, String processCode, String inspectionItem);

    List<LotInspectionStandardSnapshot> findSnapshots(
            String lotNo, String processCode);

    List<LotInspectionStandardSnapshot> saveSnapshots(
            List<LotInspectionStandardSnapshot> snapshots);

    long countSnapshots(String lotNo, String processCode);
}
