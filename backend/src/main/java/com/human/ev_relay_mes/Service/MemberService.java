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

    public List<MemberResponseDto> getMembers() {
        return memberRepository.findAll().stream().map(this::toResponse).toList();
    }

    public MemberResponseDto getMember(Long memberId) {
        return toResponse(findMember(memberId));
    }

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

    private Member findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private Member.Role parseRole(String role) {
        try {
            return Member.Role.valueOf(role.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_ROLE);
        }
    }

    private Member.Status parseStatus(String status) {
        try {
            return Member.Status.valueOf(status.toUpperCase());
        } catch (RuntimeException exception) {
            throw new CustomException(ErrorCode.INVALID_MEMBER_STATUS);
        }
    }

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
