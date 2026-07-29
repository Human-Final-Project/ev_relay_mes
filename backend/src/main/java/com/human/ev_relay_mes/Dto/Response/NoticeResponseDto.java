package com.human.ev_relay_mes.Dto.Response;

import com.human.ev_relay_mes.Entity.Notice;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class NoticeResponseDto {
    private Long noticeId;
    private String title;
    private String content;
    private boolean pinned;
    private String authorName;
    private String authorLoginId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static NoticeResponseDto fromEntity(Notice notice) {
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
