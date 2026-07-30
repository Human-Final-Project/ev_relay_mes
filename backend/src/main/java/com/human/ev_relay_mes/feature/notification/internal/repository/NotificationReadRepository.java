package com.human.ev_relay_mes.feature.notification.internal.repository;

import com.human.ev_relay_mes.feature.notification.api.NotificationRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationReadRepository extends JpaRepository<NotificationRead, Long> {

    Optional<NotificationRead> findByNotification_NotificationIdAndMember_MemberId(
            Long notificationId, Long memberId);

    List<NotificationRead> findByMember_MemberId(Long memberId);
}
