package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.Machine;

import java.util.List;

public interface MachineRepository extends JpaRepository<Machine, String> {

    // 설비 현황이나 작업 배정 화면에 운영 중인 설비 목록만 표시할 때 사용한다.
    List<Machine> findByUseYnOrderByMachineIdAsc(String useYn);

    // 대시보드에서 가동·대기·오류 상태별 설비 목록과 수량을 집계할 때 사용한다.
    List<Machine> findByStatusOrderByMachineIdAsc(Machine.Status status);

    // 작업지시나 생산 실적 처리 시 해당 공정을 수행할 설비를 찾을 때 사용한다.
    List<Machine> findByProcess_ProcessCodeOrderByMachineIdAsc(String processCode);

    // 설비 유형별 현황 조회와 동일 유형 설비 비교 화면에서 사용한다.
    List<Machine> findByMachineTypeOrderByMachineIdAsc(String machineType);

    // 신규 설비 등록 전에 동일한 설비 ID가 이미 사용 중인지 검사할 때 사용한다.
    boolean existsByMachineId(String machineId);
}
