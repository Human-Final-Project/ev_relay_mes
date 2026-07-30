package com.human.ev_relay_mes.feature.dashboard.internal.service;

import com.human.ev_relay_mes.feature.dashboard.api.WeeklyProductionTarget;
import com.human.ev_relay_mes.feature.dashboard.api.WeeklyTargetRequest;
import com.human.ev_relay_mes.feature.dashboard.api.WeeklyTargetResponse;
import com.human.ev_relay_mes.feature.dashboard.internal.repository.WeeklyProductionTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WeeklyProductionTargetService {

    private final WeeklyProductionTargetRepository repository;

    public WeeklyTargetResponse get(LocalDate requestedWeekStart) {
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        return repository.findByWeekStart(weekStart)
                .map(WeeklyTargetResponse::from)
                .orElseGet(() -> WeeklyTargetResponse.empty(weekStart));
    }

    @Transactional
    public WeeklyTargetResponse save(LocalDate requestedWeekStart, WeeklyTargetRequest request) {
        LocalDate weekStart = normalizeWeekStart(requestedWeekStart);
        WeeklyProductionTarget target = repository.findByWeekStart(weekStart)
                .orElseGet(() -> WeeklyProductionTarget.builder()
                        .weekStart(weekStart)
                        .build());
        target.setTargetQty(request.targetQty());
        target.setTargetDefectRate(request.targetDefectRate());
        return WeeklyTargetResponse.from(repository.save(target));
    }

    private LocalDate normalizeWeekStart(LocalDate requestedWeekStart) {
        LocalDate date = requestedWeekStart == null ? LocalDate.now() : requestedWeekStart;
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
