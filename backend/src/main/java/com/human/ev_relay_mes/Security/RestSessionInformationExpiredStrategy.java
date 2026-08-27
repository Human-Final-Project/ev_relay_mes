package com.human.ev_relay_mes.Security;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.web.session.SessionInformationExpiredEvent;
import org.springframework.security.web.session.SessionInformationExpiredStrategy;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class RestSessionInformationExpiredStrategy implements SessionInformationExpiredStrategy {

    private final ObjectMapper objectMapper;

    @Override
    public void onExpiredSessionDetected(SessionInformationExpiredEvent event) throws IOException {
        event.getResponse().setStatus(401);
        event.getResponse().setCharacterEncoding(StandardCharsets.UTF_8.name());
        event.getResponse().setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(event.getResponse().getWriter(), Map.of(
                "status", 401,
                "error", "UNAUTHORIZED",
                "message", "다른 로그인 또는 계정 정보 변경으로 세션이 만료되었습니다."
        ));
    }
}
