package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Process;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessRepository extends JpaRepository<Process, String> {
    boolean existsByProcessCode(String processCode);

    boolean existsByProcessOrder(Integer processOrder);

    boolean existsByProcessOrderAndProcessCodeNot(Integer processOrder, String processCode);

    Optional<Process> findFirstByUseYnOrderByProcessOrderAsc(String useYn);

    Optional<Process> findFirstByProcessOrderGreaterThanAndUseYnOrderByProcessOrderAsc(
            Integer processOrder, String useYn);

    Optional<Process> findFirstByProcessOrderLessThanAndUseYnOrderByProcessOrderDesc(
            Integer processOrder, String useYn);
}
