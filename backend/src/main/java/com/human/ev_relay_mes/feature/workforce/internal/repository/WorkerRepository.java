package com.human.ev_relay_mes.feature.workforce.internal.repository;

import com.human.ev_relay_mes.feature.workforce.api.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    boolean existsByWorkerNo(String workerNo);

    boolean existsByWorkerNoAndWorkerIdNot(String workerNo, Long workerId);

    Optional<Worker> findByWorkerNo(String workerNo);

    Optional<Worker> findByMember_MemberId(Long memberId);

    List<Worker> findAllByOrderByWorkerNoAsc();

    List<Worker> findByStatusOrderByWorkerNoAsc(Worker.Status status);
}
