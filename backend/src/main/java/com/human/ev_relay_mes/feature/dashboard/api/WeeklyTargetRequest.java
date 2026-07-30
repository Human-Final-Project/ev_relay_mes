package com.human.ev_relay_mes.feature.dashboard.api;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record WeeklyTargetRequest(
        @NotNull @Positive Integer targetQty,
        @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal targetDefectRate) {
}
