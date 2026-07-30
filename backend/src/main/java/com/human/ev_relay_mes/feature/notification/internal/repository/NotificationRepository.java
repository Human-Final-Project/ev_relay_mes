package com.human.ev_relay_mes.feature.notification.internal.repository;

import com.human.ev_relay_mes.feature.notification.api.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsByTypeAndSourceKey(
            Notification.Type type, String sourceKey);

    List<Notification> findByTypeAndSourceKeyAndResolvedAtIsNullOrderByOccurredAtDesc(
            Notification.Type type, String sourceKey);

    List<Notification> findAllByOrderByOccurredAtDescNotificationIdDesc();
}
