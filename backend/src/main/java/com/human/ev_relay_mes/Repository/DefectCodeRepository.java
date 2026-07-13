package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.DefectCode;

import java.util.List;

public interface DefectCodeRepository extends JpaRepository<DefectCode, String> {

    // 불량 코드 관리 화면이나 불량 등록용 선택 목록에 사용 가능한 코드를 표시할 때 사용한다.
    List<DefectCode> findByUseYnOrderByDefectCodeAsc(String useYn);

    // 불량 등록 시 선택한 공정에서 발생 가능한 불량 코드만 제공할 때 사용한다.
    List<DefectCode> findByProcess_ProcessCodeAndUseYnOrderByDefectCodeAsc(String processCode, String useYn);

    // 신규 불량 코드 등록 전에 동일한 코드가 이미 사용 중인지 검사할 때 사용한다.
    boolean existsByDefectCode(String defectCode);
}
