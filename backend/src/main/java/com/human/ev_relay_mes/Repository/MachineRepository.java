package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Machine;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface MachineRepository extends JpaRepository<Machine, String> {
    List<Machine> findByStatusOrderByMachineIdAsc(Machine.Status status);
    List<Machine> findByProcess_ProcessCodeOrderByMachineIdAsc(String processCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Machine m where m.process.processCode = :processCode and m.useYn = 'Y' order by m.machineId asc")
    List<Machine> findUsableByProcessForUpdate(@Param("processCode") String processCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Machine m where m.machineId = :machineId")
    Optional<Machine> findByIdForUpdate(@Param("machineId") String machineId);

    List<Machine> findByMachineTypeOrderByMachineIdAsc(String machineType);
    boolean existsByMachineId(String machineId);
}
