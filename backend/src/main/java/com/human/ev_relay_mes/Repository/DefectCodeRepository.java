package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.DefectCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DefectCodeRepository extends JpaRepository<DefectCode, String> {
    boolean existsByDefectCode(String defectCode);
}
