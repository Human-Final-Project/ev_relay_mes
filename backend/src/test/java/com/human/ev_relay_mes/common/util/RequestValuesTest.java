package com.human.ev_relay_mes.common.util;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestValuesTest {

    private enum SampleStatus {
        ACTIVE
    }

    @Test
    void trimsTextAndConvertsBlankTextToNull() {
        assertThat(RequestValues.trimToNull("  event-1  ")).isEqualTo("event-1");
        assertThat(RequestValues.trimToNull("   ")).isNull();
        assertThat(RequestValues.trimToNull(null)).isNull();
    }

    @Test
    void rejectsReversedSearchPeriod() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 30, 12, 0);
        LocalDateTime end = start.minusMinutes(1);

        assertThatThrownBy(() -> RequestValues.validateSearchPeriod(start, end))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void includesBothSearchPeriodBoundaries() {
        LocalDateTime start = LocalDateTime.of(2026, 7, 30, 9, 0);
        LocalDateTime end = start.plusHours(1);

        assertThat(RequestValues.isWithinInclusive(start, start, end)).isTrue();
        assertThat(RequestValues.isWithinInclusive(end, start, end)).isTrue();
        assertThat(RequestValues.isWithinInclusive(start.minusNanos(1), start, end)).isFalse();
        assertThat(RequestValues.isWithinInclusive(end.plusNanos(1), start, end)).isFalse();
    }

    @Test
    void parsesEnumIgnoringCaseAndOuterWhitespace() {
        assertThat(RequestValues.parseEnum(
                SampleStatus.class,
                " active ",
                ErrorCode.INVALID_INPUT_VALUE))
                .isEqualTo(SampleStatus.ACTIVE);
    }

    @Test
    void convertsInvalidEnumToRequestedDomainError() {
        assertThatThrownBy(() -> RequestValues.parseEnum(
                SampleStatus.class,
                "unknown",
                ErrorCode.INVALID_MEMBER_STATUS))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_MEMBER_STATUS);
    }
}
