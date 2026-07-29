package com.human.ev_relay_mes.feature.notice.internal.service;

import com.human.ev_relay_mes.Entity.Member;
import com.human.ev_relay_mes.Exception.CustomException;
import com.human.ev_relay_mes.Exception.ErrorCode;
import com.human.ev_relay_mes.Repository.MemberRepository;
import com.human.ev_relay_mes.feature.notice.api.NoticeRequestDto;
import com.human.ev_relay_mes.feature.notice.api.NoticeResponseDto;
import com.human.ev_relay_mes.feature.notice.internal.entity.Notice;
import com.human.ev_relay_mes.feature.notice.internal.repository.NoticeRepository;
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
                .map(this::toResponse)
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
        return toResponse(noticeRepository.save(notice));
    }

    @Transactional
    public NoticeResponseDto updateNotice(Long noticeId, NoticeRequestDto dto) {
        Notice notice = noticeRepository.findById(noticeId)
                .orElseThrow(() -> new CustomException(ErrorCode.NOTICE_NOT_FOUND));
        notice.setTitle(dto.getTitle().trim());
        notice.setContent(dto.getContent().trim());
        notice.setPinned(dto.isPinned());
        return toResponse(notice);
    }

    private NoticeResponseDto toResponse(Notice notice) {
        return NoticeResponseDto.builder()
                .noticeId(notice.getNoticeId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .pinned(notice.isPinned())
                .authorName(notice.getAuthor().getMemberName())
                .authorLoginId(notice.getAuthor().getLoginId())
                .createdAt(notice.getCreatedAt())
                .updatedAt(notice.getUpdatedAt())
                .build();
    }
}
