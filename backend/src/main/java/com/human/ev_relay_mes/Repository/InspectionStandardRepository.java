package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.InspectionStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectionStandardRepository extends JpaRepository<InspectionStandard, Long> {
    List<InspectionStandard> findByProcess_ProcessCodeOrderByStandardIdAsc(String processCode);
    Optional<InspectionStandard> findByProcess_ProcessCodeAndInspectionItem(String processCode, String inspectionItem);
    long countByProcess_ProcessCode(String processCode);
}
