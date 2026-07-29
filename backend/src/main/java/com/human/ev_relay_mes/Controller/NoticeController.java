package com.human.ev_relay_mes.Controller;

import com.human.ev_relay_mes.Dto.Request.NoticeRequestDto;
import com.human.ev_relay_mes.Dto.Response.NoticeResponseDto;
import com.human.ev_relay_mes.Service.NoticeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notices")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @GetMapping
    public List<NoticeResponseDto> getNotices() {
        return noticeService.getNotices();
    }

    @PostMapping
    public ResponseEntity<NoticeResponseDto> createNotice(
            @Valid @RequestBody NoticeRequestDto dto,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(noticeService.createNotice(dto, authentication.getName()));
    }

    @PutMapping("/{noticeId}")
    public NoticeResponseDto updateNotice(
            @PathVariable Long noticeId,
            @Valid @RequestBody NoticeRequestDto dto) {
        return noticeService.updateNotice(noticeId, dto);
    }
}
