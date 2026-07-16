package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.WorkOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface WorkOrderRepository extends JpaRepository<WorkOrder, Long> {

    boolean existsByOrderNo(String orderNo);

    List<WorkOrder> findAllByOrderByCreatedAtDesc();

    List<WorkOrder> findByStatusOrderByCreatedAtDesc(WorkOrder.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WorkOrder w where w.workOrderId = :id")
    Optional<WorkOrder> findByIdForUpdate(@Param("id") Long id);
}
