package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.LotInspectionStandardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LotInspectionStandardSnapshotRepository
        extends JpaRepository<LotInspectionStandardSnapshot, Long> {

    Optional<LotInspectionStandardSnapshot>
    findByLot_LotNoAndProcess_ProcessCodeAndInspectionItem(
            String lotNo, String processCode, String inspectionItem);

    List<LotInspectionStandardSnapshot>
    findByLot_LotNoAndProcess_ProcessCodeOrderBySnapshotIdAsc(
            String lotNo, String processCode);

    long countByLot_LotNoAndProcess_ProcessCode(String lotNo, String processCode);
}
