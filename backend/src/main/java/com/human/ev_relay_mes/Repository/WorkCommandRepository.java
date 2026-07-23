package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.WorkCommand;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WorkCommandRepository extends JpaRepository<WorkCommand, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorkCommand c where c.commandId = :id")
    Optional<WorkCommand> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorkCommand c where c.status = :status order by c.createdAt asc, c.commandId asc")
    List<WorkCommand> findByStatusForUpdate(@Param("status") WorkCommand.Status status);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorkCommand c where c.machine.machineId = :machineId "
            + "and c.status = :status order by c.createdAt asc, c.commandId asc")
    List<WorkCommand> findByMachineAndStatusForDispatch(
            @Param("machineId") String machineId,
            @Param("status") WorkCommand.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorkCommand c where c.status = :status "
            + "and c.acknowledgedAt is null and c.dispatchedAt < :cutoff")
    List<WorkCommand> findStaleDispatchedForUpdate(
            @Param("status") WorkCommand.Status status,
            @Param("cutoff") java.time.LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorkCommand c where c.machine.machineId = :machineId "
            + "and c.status = :status and c.acknowledgedAt is null "
            + "and c.dispatchedAt < :cutoff")
    List<WorkCommand> findStaleDispatchedByMachineForUpdate(
            @Param("machineId") String machineId,
            @Param("status") WorkCommand.Status status,
            @Param("cutoff") java.time.LocalDateTime cutoff);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from WorkCommand c where c.machine.machineId = :machineId "
            + "and c.status in :statuses order by c.createdAt desc, c.commandId desc")
    List<WorkCommand> findByMachineAndStatusInForUpdate(
            @Param("machineId") String machineId,
            @Param("statuses") Collection<WorkCommand.Status> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkCommand> findFirstByMachine_MachineIdAndStatusOrderByCreatedAtDescCommandIdDesc(
            String machineId, WorkCommand.Status status);

    Optional<WorkCommand> findFirstByMachine_MachineIdAndStatusInOrderByCreatedAtDescCommandIdDesc(
            String machineId, Collection<WorkCommand.Status> statuses);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkCommand> findFirstByMachine_MachineIdAndLot_LotNoAndProcess_ProcessCodeAndStatusOrderByCreatedAtDescCommandIdDesc(
            String machineId, String lotNo, String processCode, WorkCommand.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WorkCommand> findFirstByMachine_MachineIdAndLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusInOrderByCreatedAtDescCommandIdDesc(
            String machineId, String lotNo, String processCode, WorkCommand.CommandType commandType,
            Collection<WorkCommand.Status> statuses);

    boolean existsByMachine_MachineIdAndStatusIn(
            String machineId, Collection<WorkCommand.Status> statuses);

    long countByMachine_MachineIdAndStatusIn(
            String machineId, Collection<WorkCommand.Status> statuses);

    boolean existsByLot_LotNoAndProcess_ProcessCodeAndCommandTypeAndStatusIn(
            String lotNo,
            String processCode,
            WorkCommand.CommandType commandType,
            Collection<WorkCommand.Status> statuses);

    List<WorkCommand> findByLot_LotNoOrderByCreatedAtAsc(String lotNo);

    List<WorkCommand> findByLot_LotNoAndProcess_ProcessCodeAndMachine_MachineIdAndCommandTypeAndStatusIn(
            String lotNo,
            String processCode,
            String machineId,
            WorkCommand.CommandType commandType,
            Collection<WorkCommand.Status> statuses);
}
