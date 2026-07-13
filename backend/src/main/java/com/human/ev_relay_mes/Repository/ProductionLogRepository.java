package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.ProductionLog;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductionLogRepository extends JpaRepository<ProductionLog, Long> {

    // LOT 상세 화면에서 공정별 투입·양품·불량 생산 실적을 표시할 때 사용한다.
    List<ProductionLog> findByLot_LotNoOrderByCreatedAtDesc(String lotNo);

    // 설비별 생산량과 불량 실적을 분석하는 화면에서 사용한다.
    List<ProductionLog> findByMachine_MachineIdOrderByCreatedAtDesc(String machineId);

    // 공정별 생산량·수율·불량률을 집계하고 조회할 때 사용한다.
    List<ProductionLog> findByProcess_ProcessCodeOrderByCreatedAtDesc(String processCode);

    // 진행·완료·실패 상태별 생산 실적만 필터링해 표시할 때 사용한다.
    List<ProductionLog> findByStatusOrderByCreatedAtDesc(String status);

    // 대시보드와 기간 검색에서 일별·주간 생산 실적을 집계할 때 사용한다.
    List<ProductionLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime startAt, LocalDateTime endAt);
}
