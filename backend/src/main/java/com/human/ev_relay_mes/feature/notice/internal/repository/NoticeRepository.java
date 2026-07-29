package com.human.ev_relay_mes.feature.notice.internal.repository;

import com.human.ev_relay_mes.feature.notice.internal.entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findAllByOrderByPinnedDescCreatedAtDesc();
}
