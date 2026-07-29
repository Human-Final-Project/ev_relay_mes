package com.human.ev_relay_mes.feature.notice.internal;

import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.internal.repository.MemberRepository;
import com.human.ev_relay_mes.feature.notice.internal.entity.Notice;
import com.human.ev_relay_mes.feature.notice.internal.repository.NoticeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NoticeApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired NoticeRepository noticeRepository;
    @Autowired MemberRepository memberRepository;

    private Member admin;
    private Notice existingNotice;

    @BeforeEach
    void setUp() {
        noticeRepository.deleteAll();
        memberRepository.deleteAll();
        admin = memberRepository.save(member("admin", Member.Role.ADMIN));
        Member operator = memberRepository.save(member("operator", Member.Role.OPERATOR));
        existingNotice = noticeRepository.save(Notice.builder()
                .title("전체 공지")
                .content("모든 사용자가 조회할 수 있습니다.")
                .pinned(true)
                .author(admin)
                .build());
    }

    @AfterEach
    void tearDown() {
        noticeRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void everyAuthenticatedRoleCanReadNotices() throws Exception {
        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("전체 공지"))
                .andExpect(jsonPath("$[0].authorName").value("admin"));
    }

    @Test
    void unauthenticatedUserCannotReadNotices() throws Exception {
        mockMvc.perform(get("/api/notices"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanCreateNotice() throws Exception {
        mockMvc.perform(post("/api/notices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "관리자 작성 공지",
                                  "content": "시연 공지 내용입니다.",
                                  "pinned": false
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("관리자 작성 공지"))
                .andExpect(jsonPath("$.authorLoginId").value("admin"));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void nonAdminCannotCreateNotice() throws Exception {
        mockMvc.perform(post("/api/notices")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "권한 없는 공지",
                                  "content": "작성되면 안 됩니다.",
                                  "pinned": false
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void adminCanUpdateNotice() throws Exception {
        mockMvc.perform(put("/api/notices/{noticeId}", existingNotice.getNoticeId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "수정된 공지",
                                  "content": "수정된 내용입니다.",
                                  "pinned": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("수정된 공지"))
                .andExpect(jsonPath("$.pinned").value(false));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void nonAdminCannotUpdateNotice() throws Exception {
        mockMvc.perform(put("/api/notices/{noticeId}", existingNotice.getNoticeId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "권한 없는 수정",
                                  "content": "수정되면 안 됩니다.",
                                  "pinned": false
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void updatingMissingNoticeKeepsNotFoundContract() throws Exception {
        mockMvc.perform(put("/api/notices/{noticeId}", 999999)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "없는 공지",
                                  "content": "수정되면 안 됩니다.",
                                  "pinned": false
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("N001"));
    }

    private Member member(String loginId, Member.Role role) {
        return Member.builder()
                .loginId(loginId)
                .password("encoded-password")
                .memberName(loginId)
                .role(role)
                .status(Member.Status.ACTIVE)
                .build();
    }
}
