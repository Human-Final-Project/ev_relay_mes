package com.human.ev_relay_mes.feature.production.internal.service;

import com.human.ev_relay_mes.feature.production.api.LotInspectionSnapshotStore;
import com.human.ev_relay_mes.feature.production.api.LotInspectionStandardSnapshot;
import com.human.ev_relay_mes.feature.production.internal.repository.LotInspectionStandardSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LotInspectionSnapshotStoreService implements LotInspectionSnapshotStore {

    private final LotInspectionStandardSnapshotRepository snapshotRepository;

    @Override
    public Optional<LotInspectionStandardSnapshot> findSnapshot(
            String lotNo, String processCode, String inspectionItem) {
        return snapshotRepository
                .findByLot_LotNoAndProcess_ProcessCodeAndInspectionItem(
                        lotNo, processCode, inspectionItem);
    }

    @Override
    public List<LotInspectionStandardSnapshot> findSnapshots(
            String lotNo, String processCode) {
        return snapshotRepository
                .findByLot_LotNoAndProcess_ProcessCodeOrderBySnapshotIdAsc(
                        lotNo, processCode);
    }

    @Override
    @Transactional
    public List<LotInspectionStandardSnapshot> saveSnapshots(
            List<LotInspectionStandardSnapshot> snapshots) {
        return snapshotRepository.saveAll(snapshots);
    }

    @Override
    public long countSnapshots(String lotNo, String processCode) {
        return snapshotRepository.countByLot_LotNoAndProcess_ProcessCode(
                lotNo, processCode);
    }
}
