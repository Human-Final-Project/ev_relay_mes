package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.DefectHistory;

import java.time.LocalDateTime;
import java.util.List;

public interface DefectHistoryRepository extends JpaRepository<DefectHistory, Long> {

    // LOT 상세 화면에서 해당 생산 LOT에 발생한 불량 이력을 표시할 때 사용한다.
    List<DefectHistory> findByLot_LotNoOrderByOccurredAtDesc(String lotNo);

    // 설비별 품질 문제를 분석하기 위해 특정 설비의 불량 발생 내역을 조회할 때 사용한다.
    List<DefectHistory> findByMachine_MachineIdOrderByOccurredAtDesc(String machineId);

    // 공정별 불량 현황과 취약 공정을 분석하는 화면에서 사용할 이력을 가져올 때 사용한다.
    List<DefectHistory> findByProcess_ProcessCodeOrderByOccurredAtDesc(String processCode);

    // 특정 불량 유형의 발생 빈도와 관련 LOT를 추적할 때 사용한다.
    List<DefectHistory> findByDefectCode_DefectCodeOrderByOccurredAtDesc(String defectCode);

    // 일별·주간 품질 현황이나 기간 검색에서 발생한 불량 이력을 조회할 때 사용한다.
    List<DefectHistory> findByOccurredAtBetweenOrderByOccurredAtDesc(LocalDateTime startAt, LocalDateTime endAt);

    // 관리자가 아직 조치하지 않은 불량 목록을 확인하고 확인 처리할 때 사용한다.
    List<DefectHistory> findByConfirmedByIsNullOrderByOccurredAtDesc();
}
