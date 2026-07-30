package com.human.ev_relay_mes.feature.quality.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.quality.api.Inspection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InspectionRulesTest {

    private final InspectionRules rules = new InspectionRules();

    @Test
    void limitValuesAreIncludedInAcceptableRange() {
        assertThat(rules.judge(
                new BigDecimal("10.000"),
                BigDecimal.ZERO,
                new BigDecimal("10.000")))
                .isEqualTo(Inspection.Result.OK);
    }

    @Test
    void rejectsUnknownL1Judgment() {
        assertThatThrownBy(() -> rules.parseJudgment("UNKNOWN"))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INSPECTION_RESULT);
    }
}
