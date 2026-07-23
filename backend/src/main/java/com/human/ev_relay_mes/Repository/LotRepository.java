package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Lot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LotRepository extends JpaRepository<Lot, Long> {

    Optional<Lot> findByLotNo(String lotNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.lotNo = :lotNo")
    Optional<Lot> findByLotNoForUpdate(@Param("lotNo") String lotNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.lotId = :id")
    Optional<Lot> findByIdForUpdate(@Param("id") Long id);

    boolean existsByLotNo(String lotNo);

    boolean existsByWorkOrder_WorkOrderId(Long workOrderId);

    boolean existsByWorkOrder_WorkOrderIdAndStatus(Long workOrderId, Lot.Status status);

    boolean existsByWorkOrder_WorkOrderIdAndStatusIn(
            Long workOrderId, Collection<Lot.Status> statuses);

    List<Lot> findAllByOrderByCreatedAtDesc();

    List<Lot> findByStatusOrderByCreatedAtDesc(Lot.Status status);

    List<Lot> findByStatusAndStartRequestedAtIsNotNullOrderByStartRequestedAtAsc(Lot.Status status);

    List<Lot> findByWorkOrder_WorkOrderIdOrderByCreatedAtDesc(Long workOrderId);

    @Query("select coalesce(sum(l.inputQty), 0) from Lot l where l.workOrder.workOrderId = :workOrderId")
    Long sumInputQtyByWorkOrderId(@Param("workOrderId") Long workOrderId);

    @Query("select coalesce(sum(l.inputQty), 0) from Lot l "
            + "where l.workOrder.workOrderId = :workOrderId and l.status <> :excludedStatus")
    Long sumInputQtyByWorkOrderIdExcludingStatus(
            @Param("workOrderId") Long workOrderId,
            @Param("excludedStatus") Lot.Status excludedStatus);

    @Query("select coalesce(sum(l.inputQty), 0) from Lot l "
            + "where l.workOrder.workOrderId = :workOrderId and l.status = :status")
    Long sumInputQtyByWorkOrderIdAndStatus(
            @Param("workOrderId") Long workOrderId,
            @Param("status") Lot.Status status);

    @Query("select coalesce(sum(l.okQty), 0) from Lot l "
            + "where l.workOrder.workOrderId = :workOrderId and l.status = :status")
    Long sumOkQtyByWorkOrderIdAndStatus(
            @Param("workOrderId") Long workOrderId,
            @Param("status") Lot.Status status);

    @Query("select coalesce(max(l.productionRound), 0) from Lot l "
            + "where l.workOrder.workOrderId = :workOrderId")
    Integer findMaxProductionRoundByWorkOrderId(@Param("workOrderId") Long workOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Lot l where l.status = :status "
            + "order by l.workOrder.workOrderId asc, l.productionRound asc, "
            + "l.createdAt asc, l.lotId asc")
    List<Lot> findPipelineCandidatesForUpdate(@Param("status") Lot.Status status);
}
