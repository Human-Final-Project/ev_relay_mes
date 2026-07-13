package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.Inspection;

import java.time.LocalDateTime;
import java.util.List;

public interface InspectionRepository extends JpaRepository<Inspection, Long> {

    // LOT 상세 화면에서 해당 생산 LOT의 검사 결과와 합격 여부를 표시할 때 사용한다.
    List<Inspection> findByLot_LotNoOrderByInspectedAtDesc(String lotNo);

    // 설비별 검사 품질과 측정 결과 추이를 확인할 때 사용한다.
    List<Inspection> findByMachine_MachineIdOrderByInspectedAtDesc(String machineId);

    // 공정별 검사 결과와 품질 상태를 분석하는 화면에서 사용한다.
    List<Inspection> findByProcess_ProcessCodeOrderByInspectedAtDesc(String processCode);

    // 검사 결과 화면에서 OK 또는 NG 판정만 필터링해 표시할 때 사용한다.
    List<Inspection> findByResultOrderByInspectedAtDesc(Inspection.Result result);

    // 일별·주간 검사 실적과 기간별 품질 통계를 조회할 때 사용한다.
    List<Inspection> findByInspectedAtBetweenOrderByInspectedAtDesc(LocalDateTime startAt, LocalDateTime endAt);
}
