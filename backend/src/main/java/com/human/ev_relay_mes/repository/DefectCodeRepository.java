package com.human.ev_relay_mes.repository;

import com.human.ev_relay_mes.entity.DefectCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DefectCodeRepository extends JpaRepository<DefectCode, String> {

    List<DefectCode> findByUseYnOrderByDefectCodeAsc(String useYn);

    List<DefectCode> findByProcess_ProcessCodeAndUseYnOrderByDefectCodeAsc(String processCode, String useYn);

    boolean existsByDefectCode(String defectCode);
}
