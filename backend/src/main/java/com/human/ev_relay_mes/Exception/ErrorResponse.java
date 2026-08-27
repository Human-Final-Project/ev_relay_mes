package com.human.ev_relay_mes.Exception;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Builder
public class ErrorResponse {

    private final LocalDateTime timestamp;
    private final int status;
    private final String code;
    private final String message;
    private final String path;
    private final Map<String, String> errors;
}
