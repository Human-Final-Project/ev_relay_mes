package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "machine_alarm_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MachineAlarmHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "machine_alarm_history_id")
    private Long machineAlarmHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    // alarm_codes 테이블이 엔티티로 추가되어 실제 연관관계로 매핑한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alarm_code", referencedColumnName = "alarm_code", nullable = false)
    private AlarmCode alarmCode;

    @Column(name = "alarm_level", nullable = false, length = 20)
    @Builder.Default
    private String alarmLevel = "ERROR";

    @CreationTimestamp
    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "cleared_at")
    private LocalDateTime clearedAt;

    // 알람 발생은 설비가 자동 등록하고, 해제는 사용자가 처리하므로 clearedBy를 둔다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleared_by")
    private Member clearedBy;

    @Column(name = "message", length = 255)
    private String message;
}
