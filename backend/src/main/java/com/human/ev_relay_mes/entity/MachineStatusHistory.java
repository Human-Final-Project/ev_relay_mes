package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "machine_status_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MachineStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "machine_status_history_id")
    private Long machineStatusHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    // machines.status와 같은 값 집합을 쓰지만, 이 테이블은 매 순간의 스냅샷이라
    // Machine.Status를 그대로 재사용한다.
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Machine.Status status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_no", referencedColumnName = "lot_no")
    private Lot lot;

    // processes 테이블이 엔티티로 추가되어 실제 연관관계로 매핑한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_code", referencedColumnName = "process_code")
    private Process process;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private LocalDateTime recordedAt;

    @Column(name = "message", length = 255)
    private String message;

    @PrePersist
    public void prePersist() {
        this.recordedAt = LocalDateTime.now();
    }
}
