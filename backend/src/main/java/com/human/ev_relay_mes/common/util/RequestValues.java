package com.human.ev_relay_mes.common.util;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;

import java.time.LocalDateTime;

/**
 * 수집 API와 검색 API에서 반복되는 문자열·조회 기간 처리를 모은다.
 */
public final class RequestValues {

    private RequestValues() {
    }

    public static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    public static <E extends Enum<E>> E parseEnum(
            Class<E> enumType, String value, ErrorCode errorCode) {
        try {
            return Enum.valueOf(enumType, value.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(errorCode);
        }
    }

    public static void validateSearchPeriod(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new CustomException(
                    ErrorCode.INVALID_INPUT_VALUE,
                    "조회 종료 시각은 시작 시각보다 빠를 수 없습니다.");
        }
    }

    public static boolean isWithinInclusive(
            LocalDateTime value, LocalDateTime start, LocalDateTime end) {
        return (start == null || !value.isBefore(start))
                && (end == null || !value.isAfter(end));
    }
}
