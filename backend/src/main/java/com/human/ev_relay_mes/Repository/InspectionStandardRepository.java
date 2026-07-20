package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.InspectionStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InspectionStandardRepository extends JpaRepository<InspectionStandard, Long> {
    Optional<InspectionStandard> findByProcess_ProcessCodeAndInspectionItem(
            String processCode, String inspectionItem);

    List<InspectionStandard> findByProcess_ProcessCodeAndUseYnOrderByStandardIdAsc(
            String processCode, String useYn);

    List<InspectionStandard> findAllByOrderByProcess_ProcessOrderAscStandardIdAsc();

    long countByProcess_ProcessCode(String processCode);
}
