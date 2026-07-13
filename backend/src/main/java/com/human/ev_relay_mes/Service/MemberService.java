package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.MemberCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.MemberUpdateRequestDto;
import com.human.ev_relay_mes.Dto.Response.MemberResponseDto;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    private final MemberRepository memberRepository;
    private final PasswordHashService passwordHashService;

    // 관리자 회원 등록 화면에서 신규 사용자 계정과 최초 권한을 생성할 때 사용한다.
    @Transactional
    public MemberResponseDto createMember(MemberCreateRequestDto dto, Long createdById) {
        if (memberRepository.existsByLoginId(dto.getLoginId())) {
            throw new CustomException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        Member.Role role = parseRole(dto.getRole());
        Member createdBy = findMember(createdById);
        Member member = Member.builder()
                .loginId(dto.getLoginId())
                .password(passwordHashService.encode(dto.getPassword()))
                .memberName(dto.getMemberName())
                .role(role)
                .status(Member.Status.ACTIVE)
                .department(dto.getDepartment())
                .position(dto.getPosition())
                .createdBy(createdBy)
                .build();

        return toResponse(memberRepository.save(member));
    }

    // 회원 관리 화면에 전체 사용자와 권한·상태 정보를 표시할 때 사용한다.
    public List<MemberResponseDto> getMembers() {
        return memberRepository.findAll().stream().map(this::toResponse).toList();
    }

    // 회원 상세 또는 수정 화면에서 특정 사용자의 정보를 조회할 때 사용한다.
    public MemberResponseDto getMember(Long memberId) {
        return toResponse(findMember(memberId));
    }

    // 관리자가 회원의 권한·상태·부서·직급을 변경할 때 사용한다.
    @Transactional
    public MemberResponseDto updateMember(Long memberId, MemberUpdateRequestDto dto) {
        Member member = findMember(memberId);
        if (dto.getRole() != null) {
            member.setRole(parseRole(dto.getRole()));
        }
        if (dto.getStatus() != null) {
            member.setStatus(parseStatus(dto.getStatus()));
        }
        if (dto.getDepartment() != null) {
            member.setDepartment(dto.getDepartment());
        }
        if (dto.getPosition() != null) {
            member.setPosition(dto.getPosition());
        }
        return toResponse(member);
    }

    // 회원 관련 업무에서 대상 회원의 존재 여부를 확인하고 Entity를 가져올 때 사용한다.
    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    // 화면에서 받은 권한 문자열을 Member.Role Enum으로 안전하게 변환할 때 사용한다.
    private Member.Role parseRole(String role) {
        try {
            return Member.Role.valueOf(role.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_ROLE);
        }
    }

    // 화면에서 받은 계정 상태 문자열을 Member.Status Enum으로 안전하게 변환할 때 사용한다.
    private Member.Status parseStatus(String status) {
        try {
            return Member.Status.valueOf(status.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_STATUS);
        }
    }

    // 비밀번호를 제외한 회원 정보를 회원 관리 API의 응답 DTO로 변환할 때 사용한다.
    private MemberResponseDto toResponse(Member member) {
        Member creator = member.getCreatedBy();
        return MemberResponseDto.builder()
                .memberId(member.getMemberId())
                .loginId(member.getLoginId())
                .memberName(member.getMemberName())
                .role(member.getRole().name())
                .status(member.getStatus().name())
                .department(member.getDepartment())
                .position(member.getPosition())
                .createdById(creator == null ? null : creator.getMemberId())
                .createdByName(creator == null ? null : creator.getMemberName())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .build();
    }
}
