package com.human.ev_relay_mes.repository;

import com.human.ev_relay_mes.entity.Machine;
import com.human.ev_relay_mes.entity.MachineStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface MachineStatusHistoryRepository extends JpaRepository<MachineStatusHistory, Long> {

    List<MachineStatusHistory> findByMachine_MachineIdOrderByRecordedAtDesc(String machineId);

    List<MachineStatusHistory> findByLot_LotNoOrderByRecordedAtDesc(String lotNo);

    List<MachineStatusHistory> findByProcess_ProcessCodeOrderByRecordedAtDesc(String processCode);

    List<MachineStatusHistory> findByStatusOrderByRecordedAtDesc(Machine.Status status);

    List<MachineStatusHistory> findByRecordedAtBetweenOrderByRecordedAtDesc(LocalDateTime startAt, LocalDateTime endAt);
}
