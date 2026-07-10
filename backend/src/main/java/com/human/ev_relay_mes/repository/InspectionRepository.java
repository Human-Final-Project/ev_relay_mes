package com.human.ev_relay_mes.repository;

import com.human.ev_relay_mes.entity.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    List<Inspection> findByLot_LotNoOrderByInspectedAtDesc(String lotNo);

    List<Inspection> findByMachine_MachineIdOrderByInspectedAtDesc(String machineId);

    List<Inspection> findByProcess_ProcessCodeOrderByInspectedAtDesc(String processCode);

    List<Inspection> findByResultOrderByInspectedAtDesc(Inspection.Result result);

    List<Inspection> findByInspectedAtBetweenOrderByInspectedAtDesc(LocalDateTime startAt, LocalDateTime endAt);
}
