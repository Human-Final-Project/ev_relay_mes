package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.PasswordChangeRequestDto;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MemberRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock MemberRepository memberRepository;

    private PasswordEncoder passwordEncoder;
    private MemberService memberService;
    private Member member;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        memberService = new MemberService(memberRepository, passwordEncoder);
        member = Member.builder()
                .memberId(1L)
                .loginId("operator1")
                .password(passwordEncoder.encode("old-password"))
                .memberName("operator")
                .role(Member.Role.OPERATOR)
                .status(Member.Status.ACTIVE)
                .build();
        when(memberRepository.findById(1L)).thenReturn(Optional.of(member));
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

    @Test
    void resetsPasswordAndReturnsPlainTemporaryPasswordOnlyInResponse() {
        String originalPassword = member.getPassword();

        var response = memberService.resetPassword(1L);

        assertThat(response.getMemberId()).isEqualTo(1L);
        assertThat(response.getLoginId()).isEqualTo("operator1");
        assertThat(response.getTemporaryPassword()).hasSize(12);
        assertThat(member.getPassword()).isNotEqualTo(originalPassword);
        assertThat(member.getPassword()).isNotEqualTo(response.getTemporaryPassword());
        assertThat(passwordEncoder.matches(response.getTemporaryPassword(), member.getPassword())).isTrue();
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
