package com.human.ev_relay_mes.feature.quality.api;

import java.util.List;

/**
 * 검사 결과 수신과 조회에 사용하는 공개 계약입니다.
 */
public interface InspectionOperations {

    InspectionResponseDto saveResult(InspectionResultReceiveRequestDto dto);

    InspectionUnitResultResponseDto saveJudgment(UnitJudgmentReceiveRequestDto dto);

    List<InspectionResponseDto> search(InspectionSearchRequestDto condition);
}
