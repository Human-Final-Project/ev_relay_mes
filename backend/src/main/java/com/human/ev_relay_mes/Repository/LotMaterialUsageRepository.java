package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.LotMaterialUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface LotMaterialUsageRepository extends JpaRepository<LotMaterialUsage, Long> {

    @Query("""
            select u
            from LotMaterialUsage u
            join fetch u.lot lot
            join fetch u.materialLot materialLot
            join fetch materialLot.item item
            where lot.lotNo = :lotNo
            order by item.itemCode asc, materialLot.receivedAt asc
            """)
    List<LotMaterialUsage> findByLotNo(@Param("lotNo") String lotNo);
}
