package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    Optional<Inspection> findByEventId(String eventId);

    Optional<Inspection> findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqAndInspectionItem(
            String lotNo, String processCode, Integer unitSeq, String inspectionItem);

    List<Inspection> findByLot_LotNoAndProcess_ProcessCodeAndUnitSeqOrderByInspectionIdAsc(
            String lotNo, String processCode, Integer unitSeq);

    long countByLot_LotNoAndProcess_ProcessCodeAndUnitSeq(
            String lotNo, String processCode, Integer unitSeq);

    List<Inspection> findByLot_LotNoOrderByInspectedAtDesc(String lotNo);
    List<Inspection> findByMachine_MachineIdOrderByInspectedAtDesc(String machineId);
    List<Inspection> findByProcess_ProcessCodeOrderByInspectedAtDesc(String processCode);
    List<Inspection> findByResultOrderByInspectedAtDesc(Inspection.Result result);
    List<Inspection> findByInspectedAtBetweenOrderByInspectedAtDesc(
            LocalDateTime startAt, LocalDateTime endAt);
}
