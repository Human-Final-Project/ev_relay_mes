package com.human.ev_relay_mes.Service;

import com.human.ev_relay_mes.Dto.Request.NoticeRequestDto;
import com.human.ev_relay_mes.Dto.Response.NoticeResponseDto;
import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Entity.Notice;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.Repository.NoticeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NoticeService {

    private final NoticeRepository noticeRepository;
    private final MemberRepository memberRepository;

    public List<NoticeResponseDto> getNotices() {
        return noticeRepository.findAllByOrderByPinnedDescCreatedAtDesc().stream()
                .map(NoticeResponseDto::fromEntity)
                .toList();
    }

    @Transactional
    public NoticeResponseDto createNotice(NoticeRequestDto dto, String authorLoginId) {
        Member author = memberRepository.findByLoginId(authorLoginId)
                .orElseThrow(() -> new CustomException(ErrorCode.MEMBER_NOT_FOUND));
        Notice notice = Notice.builder()
                .title(dto.getTitle().trim())
                .content(dto.getContent().trim())
                .pinned(dto.isPinned())
                .author(author)
                .build();
        return NoticeResponseDto.fromEntity(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponseDto updateNotice(Long noticeId, NoticeRequestDto dto) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
        notice.setTitle(dto.getTitle().trim());
        notice.setContent(dto.getContent().trim());
        notice.setPinned(dto.isPinned());
        return NoticeResponseDto.fromEntity(notice);
    }
}
