package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
class MemberRepositoryTest extends RepositoryTestSupport {

    @Autowired MemberRepository memberRepository;

    @Test
    void 로그인아이디와_권한_상태로_회원을_조회한다() {
        Member admin = member("admin", Member.Role.ADMIN, Member.Status.ACTIVE);
        member("manager", Member.Role.MANAGER, Member.Status.ACTIVE);
        member("retired-admin", Member.Role.ADMIN, Member.Status.RETIRED);

        assertThat(memberRepository.findByLoginId("admin"))
                .get().extracting(Member::getMemberId).isEqualTo(admin.getMemberId());
        assertThat(memberRepository.existsByLoginId("admin")).isTrue();
        assertThat(memberRepository.findByRoleAndStatusOrderByMemberIdAsc(
                Member.Role.ADMIN, Member.Status.ACTIVE))
                .extracting(Member::getLoginId)
                .containsExactly("admin");
        assertThat(memberRepository.findByStatusOrderByMemberIdAsc(Member.Status.ACTIVE))
                .extracting(Member::getLoginId)
                .containsExactly("admin", "manager");
    }
}
