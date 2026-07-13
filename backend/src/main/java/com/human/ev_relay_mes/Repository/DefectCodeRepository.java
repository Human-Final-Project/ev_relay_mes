package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.DefectCode;

import java.util.List;

public interface DefectCodeRepository extends JpaRepository<DefectCode, String> {

    List<DefectCode> findByUseYnOrderByDefectCodeAsc(String useYn);

    List<DefectCode> findByProcess_ProcessCodeAndUseYnOrderByDefectCodeAsc(String processCode, String useYn);

    boolean existsByDefectCode(String defectCode);
}
