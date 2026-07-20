package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.LotProcessResponsible;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LotProcessResponsibleRepository
        extends JpaRepository<LotProcessResponsible, Long> {

    Optional<LotProcessResponsible> findByLot_LotNoAndProcess_ProcessCode(
            String lotNo,
            String processCode);

    List<LotProcessResponsible> findByLot_LotNoOrderByProcess_ProcessOrderAsc(String lotNo);
}
