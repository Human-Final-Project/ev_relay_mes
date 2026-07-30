package com.human.ev_relay_mes.feature.dashboard.internal.repository;

import com.human.ev_relay_mes.feature.dashboard.api.WeeklyProductionTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyProductionTargetRepository
        extends JpaRepository<WeeklyProductionTarget, Long> {

    Optional<WeeklyProductionTarget> findByWeekStart(LocalDate weekStart);
}
