package com.human.ev_relay_mes.feature.masterdata.internal.repository;

import com.human.ev_relay_mes.feature.masterdata.api.Process;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessRepository extends JpaRepository<Process, String> {
    boolean existsByProcessOrder(Integer processOrder);
    boolean existsByProcessOrderAndProcessCodeNot(Integer processOrder, String processCode);
    Optional<Process> findFirstByOrderByProcessOrderAsc();
    Optional<Process> findFirstByProcessOrderGreaterThanOrderByProcessOrderAsc(Integer processOrder);
    Optional<Process> findFirstByProcessOrderLessThanOrderByProcessOrderDesc(Integer processOrder);
}
