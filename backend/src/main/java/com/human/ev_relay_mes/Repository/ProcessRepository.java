package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Process;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessRepository extends JpaRepository<Process, String> {
}
