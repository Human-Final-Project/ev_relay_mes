package com.human.ev_relay_mes.Security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class MemberSessionService {

    private final SessionRegistry sessionRegistry;

    public void expireAllSessions(Long memberId) {
        sessionRegistry.getAllPrincipals().stream()
                .filter(CustomUserDetails.class::isInstance)
                .map(CustomUserDetails.class::cast)
                .filter(userDetails -> Objects.equals(userDetails.getMemberId(), memberId))
                .flatMap(userDetails -> sessionRegistry.getAllSessions(userDetails, false).stream())
                .forEach(SessionInformation::expireNow);
    }
}
