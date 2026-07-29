package com.human.ev_relay_mes.feature.quality.api;

import java.util.List;

/**
 * 불량 이력 등록과 조회에 사용하는 공개 계약입니다.
 */
public interface DefectOperations {

    DefectHistoryResponseDto createDefect(DefectHistoryCreateRequestDto dto);

    List<DefectHistoryResponseDto> search(DefectHistorySearchRequestDto condition);
}
