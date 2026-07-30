package com.human.ev_relay_mes.feature.dashboard.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record WeeklyTargetResponse(
        LocalDate weekStart,
        Integer targetQty,
        BigDecimal targetDefectRate,
        boolean configured,
        LocalDateTime updatedAt) {

    public static WeeklyTargetResponse from(WeeklyProductionTarget target) {
        return new WeeklyTargetResponse(
                target.getWeekStart(),
                target.getTargetQty(),
                target.getTargetDefectRate(),
                true,
                target.getUpdatedAt());
    }

    public static WeeklyTargetResponse empty(LocalDate weekStart) {
        return new WeeklyTargetResponse(
                weekStart,
                0,
                BigDecimal.valueOf(5),
                false,
                null);
    }
}
