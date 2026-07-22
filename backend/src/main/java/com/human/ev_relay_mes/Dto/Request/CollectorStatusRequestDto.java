package com.human.ev_relay_mes.Dto.Request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CollectorStatusRequestDto(
        @NotBlank String collectorId,
        @Min(0) @Max(100) int connectedL1,
        @Min(1) @Max(100) int totalL1) {

    @AssertTrue(message = "연결된 L1 수는 전체 L1 수를 초과할 수 없습니다.")
    public boolean isConnectionCountValid() {
        return connectedL1 <= totalL1;
    }
}
