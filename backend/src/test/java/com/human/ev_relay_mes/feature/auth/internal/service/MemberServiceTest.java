package com.human.ev_relay_mes.feature.auth.internal.service;

import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.feature.auth.api.Member;
import com.human.ev_relay_mes.feature.auth.api.MemberCreateRequestDto;
import com.human.ev_relay_mes.feature.auth.api.MemberUpdateRequestDto;
import com.human.ev_relay_mes.feature.auth.api.PasswordChangeRequestDto;
import com.human.ev_relay_mes.feature.auth.internal.repository.MemberRepository;
import com.human.ev_relay_mes.feature.workforce.api.WorkforceMemberLinkOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;
    @Mock MemberSessionService memberSessionService;
    @Mock WorkforceMemberLinkOperations workforceMemberLinkOperations;

    private PasswordEncoder passwordEncoder;
    private MemberService memberService;
    private Member member;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        memberService = new MemberService(
                memberRepository,
                passwordEncoder,
                memberSessionService,
                workforceMemberLinkOperations);
        member = Member.builder()
                .memberId(1L)
                .loginId("operator1")
                .password(passwordEncoder.encode("old-password"))
                .memberName("operator")
                .role(Member.Role.OPERATOR)
                .status(Member.Status.ACTIVE)
                .build();
        lenient().when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
    }


    @Test
    void createsMemberWithSelectedStatus() {
        MemberCreateRequestDto dto = new MemberCreateRequestDto();
        dto.setLoginId("EVR00000007");
        dto.setPassword("password");
        dto.setMemberName("operator2");
        dto.setRole("OPERATOR");
        dto.setStatus("LOCKED");
        when(memberRepository.save(any(Member.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = memberService.createMember(dto, 1L);

        assertThat(response.getRole()).isEqualTo("OPERATOR");
        assertThat(response.getStatus()).isEqualTo("LOCKED");
    }

    @Test
    void rejectsOperatorLoginIdThatIsNotFixedEmployeeNumberFormat() {
        MemberCreateRequestDto dto = new MemberCreateRequestDto();
        dto.setLoginId("operator2");
        dto.setPassword("password");
        dto.setMemberName("operator2");
        dto.setRole("OPERATOR");

        assertThatThrownBy(() -> memberService.createMember(dto, 1L))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void expiresExistingSessionsWhenRoleOrStatusChanges() {
        MemberUpdateRequestDto dto = new MemberUpdateRequestDto();
        dto.setStatus("LOCKED");

        memberService.updateMember(1L, dto);

        assertThat(member.getStatus()).isEqualTo(Member.Status.LOCKED);
        verify(memberSessionService).expireAllSessions(1L);
    }

    @Test
    void keepsExistingSessionsWhenRoleAndStatusDoNotActuallyChange() {
        MemberUpdateRequestDto dto = new MemberUpdateRequestDto();
        dto.setRole("OPERATOR");
        dto.setStatus("ACTIVE");

        memberService.updateMember(1L, dto);

        verify(memberSessionService, never()).expireAllSessions(1L);
    }

    @Test
    void changesPasswordWhenCurrentPasswordAndConfirmationAreValid() {
        PasswordChangeRequestDto dto = passwordChangeRequest(
                "old-password", "new-password", "new-password");

        memberService.changePassword(1L, dto);

        assertThat(passwordEncoder.matches("new-password", member.getPassword())).isTrue();
        assertThat(passwordEncoder.matches("old-password", member.getPassword())).isFalse();
    }

    @Test
    void rejectsPasswordChangeWhenCurrentPasswordDoesNotMatch() {
        String originalPassword = member.getPassword();
        PasswordChangeRequestDto dto = passwordChangeRequest(
                "wrong-password", "new-password", "new-password");

        assertThatThrownBy(() -> memberService.changePassword(1L, dto))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CURRENT_PASSWORD_MISMATCH);
        assertThat(member.getPassword()).isEqualTo(originalPassword);
    }

    @Test
    void rejectsPasswordChangeWhenConfirmationDoesNotMatch() {
        String originalPassword = member.getPassword();
        PasswordChangeRequestDto dto = passwordChangeRequest(
                "old-password", "new-password", "different-password");

        assertThatThrownBy(() -> memberService.changePassword(1L, dto))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NEW_PASSWORD_MISMATCH);
        assertThat(member.getPassword()).isEqualTo(originalPassword);
    }


    private PasswordChangeRequestDto passwordChangeRequest(
            String currentPassword, String newPassword, String confirmation) {
        PasswordChangeRequestDto dto = new PasswordChangeRequestDto();
        dto.setCurrentPassword(currentPassword);
        dto.setNewPassword(newPassword);
        dto.setNewPasswordConfirm(confirmation);
        return dto;
    }
}
