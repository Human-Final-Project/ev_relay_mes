package com.human.ev_relay_mes.Repository;

import com.human.ev_relay_mes.Entity.Notice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NoticeRepository extends JpaRepository<Notice, Long> {
    List<Notice> findAllByOrderByPinnedDescCreatedAtDesc();
}
