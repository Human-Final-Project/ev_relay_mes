package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "alarm_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlarmCode {

    // PK가 HV_TESTER_ERROR 같은 업무 코드라서 직접 세팅한다.
    @Id
    @Column(name = "alarm_code", length = 50)
    private String alarmCode;

    @Column(name = "alarm_name", nullable = false, length = 100)
    private String alarmName;

    // 알람은 특정 공정이 아니라 설비 유형(EQ-WIND, COMMON 등) 기준으로 분류된다.
    @Column(name = "machine_type", nullable = false, length = 30)
    private String machineType;

    @Column(name = "description", length = 255)
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
