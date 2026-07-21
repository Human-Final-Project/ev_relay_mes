package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.MaterialLot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MaterialLotRepository extends JpaRepository<MaterialLot, Long> {
    boolean existsByMaterialLotNo(String materialLotNo);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ml from MaterialLot ml "
            + "where ml.item.itemCode = :itemCode "
            + "and ml.status = :status and ml.currentQty > 0 "
            + "order by ml.receivedAt asc, ml.materialLotId asc")
    List<MaterialLot> findAvailableLotsForUpdate(
            @Param("itemCode") String itemCode,
            @Param("status") MaterialLot.Status status);

    @Query("select coalesce(sum(ml.currentQty), 0) from MaterialLot ml "
            + "where ml.item.itemCode = :itemCode "
            + "and ml.status = :status and ml.currentQty > 0")
    Long sumAvailableQty(
            @Param("itemCode") String itemCode,
            @Param("status") MaterialLot.Status status);
}
