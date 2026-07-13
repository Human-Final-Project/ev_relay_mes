package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.DefectHistory;

import java.time.LocalDateTime;
import java.util.List;

public interface DefectHistoryRepository extends JpaRepository<DefectHistory, Long> {

    List<DefectHistory> findByLot_LotNoOrderByOccurredAtDesc(String lotNo);

    List<DefectHistory> findByMachine_MachineIdOrderByOccurredAtDesc(String machineId);

    List<DefectHistory> findByProcess_ProcessCodeOrderByOccurredAtDesc(String processCode);

    List<DefectHistory> findByDefectCode_DefectCodeOrderByOccurredAtDesc(String defectCode);

    List<DefectHistory> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime startAt, LocalDateTime endAt);

    List<DefectHistory> findByConfirmedByIsNullOrderByOccurredAtDesc();
}
