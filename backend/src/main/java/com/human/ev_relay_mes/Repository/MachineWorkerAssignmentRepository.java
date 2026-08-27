package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.MachineWorkerAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MachineWorkerAssignmentRepository
        extends JpaRepository<MachineWorkerAssignment, Long> {

    List<MachineWorkerAssignment> findByMachine_MachineIdOrderByAssignmentRoleAscAssignedAtAsc(
            String machineId);

    Optional<MachineWorkerAssignment> findByMachine_MachineIdAndAssignmentRole(
            String machineId,
            MachineWorkerAssignment.AssignmentRole assignmentRole);

    Optional<MachineWorkerAssignment> findByMachine_MachineIdAndWorker_WorkerId(
            String machineId,
            Long workerId);

    boolean existsByWorker_WorkerId(Long workerId);

    void deleteByMachine_MachineIdAndWorker_WorkerId(String machineId, Long workerId);
}
