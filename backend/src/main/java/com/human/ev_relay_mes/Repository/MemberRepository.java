package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.Member;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    // 로그인 요청에서 계정 정보와 비밀번호 해시·상태·권한을 확인할 때 사용한다.
    Optional<Member> findByLoginId(String loginId);

    // 회원 관리 화면에서 관리자·작업자 등 권한별 사용자만 필터링할 때 사용한다.
    List<Member> findByRoleOrderByMemberIdAsc(Member.Role role);

    // 회원 관리 화면에서 활성·잠김·퇴직 상태별 사용자 목록을 조회할 때 사용한다.
    List<Member> findByStatusOrderByMemberIdAsc(Member.Status status);

    // 특정 권한을 가진 활성 사용자처럼 권한과 상태를 함께 검색할 때 사용한다.
    List<Member> findByRoleAndStatusOrderByMemberIdAsc(Member.Role role, Member.Status status);

    // 관리자 회원 등록 시 로그인 ID 중복을 사전에 차단할 때 사용한다.
    boolean existsByLoginId(String loginId);
}
