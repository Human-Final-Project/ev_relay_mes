package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.MemberCreateRequestDto;
import com.human.ev_relay_mes.Dto.Request.MemberUpdateRequestDto;
import com.human.ev_relay_mes.Dto.Response.MemberResponseDto;
import com.human.ev_relay_mes.Security.CustomUserDetails;
import com.human.ev_relay_mes.Service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @PostMapping
    public ResponseEntity<MemberResponseDto> createMember(
            @Valid @RequestBody MemberCreateRequestDto dto,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(memberService.createMember(dto, userDetails.getMemberId()));
    }

    @GetMapping
    public List<MemberResponseDto> getMembers() {
        return memberService.getMembers();
    }

    @GetMapping("/{id}")
    public MemberResponseDto getMember(@PathVariable Long id) {
        return memberService.getMember(id);
    }

    @PatchMapping("/{id}")
    public MemberResponseDto updateMember(
            @PathVariable Long id, @Valid @RequestBody MemberUpdateRequestDto dto) {
        return memberService.updateMember(id, dto);
    }

}
