package com.human.ev_relay_mes.Security;

import com.human.ev_relay_mes.Entity.Member;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class CustomUserDetails implements UserDetails {

    private final Long memberId;
    private final String loginId;
    private final String password;
    private final String memberName;
    private final Member.Role role;
    private final Member.Status status;

    private CustomUserDetails(Member member) {
        this.memberId = member.getMemberId();
        this.loginId = member.getLoginId();
        this.password = member.getPassword();
        this.memberName = member.getMemberName();
        this.role = member.getRole();
        this.status = member.getStatus();
    }

    // DB에서 조회한 회원 정보를 Spring Security가 인증에 사용할 사용자 정보로 변환한다.
    public static CustomUserDetails from(Member member) {
        return new CustomUserDetails(member);
    }

    public Long getMemberId() {
        return memberId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getMemberName() {
        return memberName;
    }

    public Member.Role getRole() {
        return role;
    }

    public Member.Status getStatus() {
        return status;
    }

    // Member 역할을 ROLE_ADMIN과 같은 Spring Security 권한으로 제공한다.
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof CustomUserDetails other)) {
            return false;
        }
        return Objects.equals(memberId, other.memberId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(memberId);
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    // LOCKED 상태 회원의 로그인을 Spring Security 인증 단계에서 차단한다.
    @Override
    public boolean isAccountNonLocked() {
        return status != Member.Status.LOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    // RETIRED 상태 회원의 로그인을 Spring Security 인증 단계에서 차단한다.
    @Override
    public boolean isEnabled() {
        return status != Member.Status.RETIRED;
    }
}
