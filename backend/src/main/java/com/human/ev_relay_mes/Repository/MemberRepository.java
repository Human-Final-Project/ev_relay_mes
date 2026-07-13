package com.human.ev_relay_mes.Repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.human.ev_relay_mes.Entity.Member;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByLoginId(String loginId);

    List<Member> findByRoleOrderByMemberIdAsc(Member.Role role);

    List<Member> findByStatusOrderByMemberIdAsc(Member.Status status);

    List<Member> findByRoleAndStatusOrderByMemberIdAsc(Member.Role role, Member.Status status);

    boolean existsByLoginId(String loginId);
}
