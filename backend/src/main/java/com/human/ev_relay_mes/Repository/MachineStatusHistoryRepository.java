package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.Machine;
import com.human.ev_relay_mes.Entity.MachineStatusHistory;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MachineStatusHistoryRepository extends JpaRepository<MachineStatusHistory, Long> {

    Optional<MachineStatusHistory> findByEventId(String eventId);

    // 설비 상세 화면에서 가동·대기·오류 상태의 변경 이력을 표시할 때 사용한다.
    List<MachineStatusHistory> findByMachine_MachineIdOrderByRecordedAtDesc(String machineId);

    Optional<MachineStatusHistory> findFirstByMachine_MachineIdOrderByRecordedAtDesc(String machineId);

    // LOT 생산 과정에서 어떤 설비 상태 변화가 발생했는지 추적할 때 사용한다.
    List<MachineStatusHistory> findByLot_LotNoOrderByRecordedAtDesc(String lotNo);

    // 공정별 설비 가동 상태와 장애 이력을 분석할 때 사용한다.
    List<MachineStatusHistory> findByProcess_ProcessCodeOrderByRecordedAtDesc(String processCode);

    // 오류 또는 가동 상태만 필터링해 설비 운영 현황을 확인할 때 사용한다.
    List<MachineStatusHistory> findByStatusOrderByRecordedAtDesc(Machine.Status status);

    // 기간별 설비 가동 추이와 상태 변화 내역을 조회할 때 사용한다.
    List<MachineStatusHistory> findByRecordedAtBetweenOrderByRecordedAtDesc(LocalDateTime startAt, LocalDateTime endAt);
}
