package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "production_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductionLog {

    @Column(name = "event_id", unique = true, length = 100)
    private String eventId;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "production_log_id")
    private Long productionLogId;

    // L2가 실적을 보낼 때는 lot_no 문자열 기준으로 들어오므로 lots.lot_no를 참조한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_no", referencedColumnName = "lot_no", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    // processes 테이블이 엔티티로 추가되어 실제 연관관계로 매핑한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_code", referencedColumnName = "process_code", nullable = false)
    private Process process;

    @Column(name = "input_qty", nullable = false)
    @Builder.Default
    private Integer inputQty = 0;

    @Column(name = "ok_qty", nullable = false)
    @Builder.Default
    private Integer okQty = 0;

    @Column(name = "ng_qty", nullable = false)
    @Builder.Default
    private Integer ngQty = 0;

    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private String status = "COMPLETED";

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
