package com.human.ev_relay_mes.feature.notice.api;

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
}
