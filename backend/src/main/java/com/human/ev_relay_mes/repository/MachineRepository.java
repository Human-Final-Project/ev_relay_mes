package com.human.ev_relay_mes.repository;

import com.human.ev_relay_mes.entity.Machine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MachineRepository extends JpaRepository<Machine, String> {

    List<Machine> findByUseYnOrderByMachineIdAsc(String useYn);

    List<Machine> findByStatusOrderByMachineIdAsc(Machine.Status status);

    List<Machine> findByProcess_ProcessCodeOrderByMachineIdAsc(String processCode);

    List<Machine> findByMachineTypeOrderByMachineIdAsc(String machineType);

    boolean existsByMachineId(String machineId);
}
