package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Bom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BomRepository extends JpaRepository<Bom, Long> {
    List<Bom> findByParentItemCode(String parentItemCode);
}