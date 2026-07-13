package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.ProductionLog;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductionLogRepository extends JpaRepository<ProductionLog, Long> {

    List<ProductionLog> findByLot_LotNoOrderByCreatedAtDesc(String lotNo);

    List<ProductionLog> findByMachine_MachineIdOrderByCreatedAtDesc(String machineId);

    List<ProductionLog> findByProcess_ProcessCodeOrderByCreatedAtDesc(String processCode);

    List<ProductionLog> findByStatusOrderByCreatedAtDesc(String status);

    List<ProductionLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startAt, LocalDateTime endAt);
}
