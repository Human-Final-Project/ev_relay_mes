package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Worker;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkerRepository extends JpaRepository<Worker, Long> {

    boolean existsByWorkerNo(String workerNo);

    boolean existsByWorkerNoAndWorkerIdNot(String workerNo, Long workerId);

    List<Worker> findAllByOrderByWorkerNoAsc();

    List<Worker> findByStatusOrderByWorkerNoAsc(Worker.Status status);
}
