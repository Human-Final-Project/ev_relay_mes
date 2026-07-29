package com.human.ev_relay_mes.feature.masterdata.internal.repository;

import com.human.ev_relay_mes.feature.masterdata.api.InspectionStandard;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InspectionStandardRepository extends JpaRepository<InspectionStandard, Long> {
    List<InspectionStandard> findByProcess_ProcessCodeOrderByStandardIdAsc(String processCode);
    long countByProcess_ProcessCode(String processCode);
}
