package com.human.ev_relay_mes.feature.dashboard.internal.service;

import com.human.ev_relay_mes.feature.dashboard.api.WeeklyProductionTarget;
import com.human.ev_relay_mes.feature.dashboard.api.WeeklyTargetRequest;
import com.human.ev_relay_mes.feature.dashboard.api.WeeklyTargetResponse;
import com.human.ev_relay_mes.feature.dashboard.internal.repository.WeeklyProductionTargetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyProductionTargetServiceTest {

    @Mock
    private WeeklyProductionTargetRepository repository;

    private WeeklyProductionTargetService service;

    @BeforeEach
    void setUp() {
        service = new WeeklyProductionTargetService(repository);
    }

    @Test
    void returnsAnUnconfiguredDefaultForTheRequestedWeek() {
        LocalDate monday = LocalDate.of(2026, 7, 27);
        when(repository.findByWeekStart(monday)).thenReturn(Optional.empty());

        WeeklyTargetResponse response = service.get(LocalDate.of(2026, 7, 30));

        assertThat(response.weekStart()).isEqualTo(monday);
        assertThat(response.targetQty()).isZero();
        assertThat(response.targetDefectRate()).isEqualByComparingTo("5");
        assertThat(response.configured()).isFalse();
    }

    @Test
    void savesOneTargetAgainstTheNormalizedMonday() {
        LocalDate monday = LocalDate.of(2026, 7, 27);
        when(repository.findByWeekStart(monday)).thenReturn(Optional.empty());
        when(repository.save(any(WeeklyProductionTarget.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        WeeklyTargetResponse response = service.save(
                LocalDate.of(2026, 7, 30),
                new WeeklyTargetRequest(1_000, BigDecimal.valueOf(3.5)));

        assertThat(response.weekStart()).isEqualTo(monday);
        assertThat(response.targetQty()).isEqualTo(1_000);
        assertThat(response.targetDefectRate()).isEqualByComparingTo("3.5");
        assertThat(response.configured()).isTrue();
        verify(repository).save(any(WeeklyProductionTarget.class));
    }
}
