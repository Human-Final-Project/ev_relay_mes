package com.human.ev_relay_mes;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemberPasswordApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired ObjectMapper objectMapper;

    private Member operator;

    @BeforeEach
    void setUp() {
        memberRepository.deleteAll();
        operator = memberRepository.save(member("operator", "old-password", Member.Role.OPERATOR));
    }

    @Test
    void authenticatedMemberChangesPasswordAndIsLoggedOut() throws Exception {
        MockHttpSession session = login("operator", "old-password");

        mockMvc.perform(patch("/api/auth/password")
                        .session(session)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "currentPassword": "old-password",
                                  "newPassword": "new-password",
                                  "newPasswordConfirm": "new-password"
                                }
                                """))
                .andExpect(status().isNoContent());

        assertThat(session.isInvalid()).isTrue();
        Member changed = memberRepository.findById(operator.getMemberId()).orElseThrow();
        assertThat(passwordEncoder.matches("new-password", changed.getPassword())).isTrue();
        login("operator", "new-password");
    }


    @Test
    void newLoginExpiresPreviousSessionAndKeepsLatestSession() throws Exception {
        MockHttpSession previousSession = login("operator", "old-password");
        MockHttpSession latestSession = login("operator", "old-password");

        assertExpired(previousSession);
        mockMvc.perform(get("/api/auth/me").session(latestSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loginId").value("operator"));
    }


    private MockHttpSession login(String loginId, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginBody(loginId, password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void assertExpired(MockHttpSession session) throws Exception {
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message")
                        .value("다른 로그인 또는 계정 정보 변경으로 세션이 만료되었습니다."));
        assertThat(session.isInvalid()).isTrue();
    }

    private Member member(String loginId, String password, Member.Role role) {
        return Member.builder()
                .loginId(loginId)
                .password(passwordEncoder.encode(password))
                .memberName(loginId)
                .role(role)
                .status(Member.Status.ACTIVE)
                .build();
    }

    private record LoginBody(String loginId, String password) {
    }
}
