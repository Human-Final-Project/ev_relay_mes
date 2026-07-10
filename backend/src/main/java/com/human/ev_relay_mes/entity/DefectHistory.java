package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "defect_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DefectHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "defect_history_id")
    private Long defectHistoryId;

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

    // defect_codes 테이블이 엔티티로 추가되어 실제 연관관계로 매핑한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "defect_code", referencedColumnName = "defect_code", nullable = false)
    private DefectCode defectCode;

    @Column(name = "defect_qty", nullable = false)
    @Builder.Default
    private Integer defectQty = 0;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private LocalDateTime occurredAt;

    @Column(name = "message", length = 255)
    private String message;

    // 설비/시뮬레이터가 자동 등록하는 경우가 많아 필수값이 아니다.
    // 사람이 확인 처리한 경우에만 값이 채워진다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private Member confirmedBy;

    @PrePersist
    public void prePersist() {
        this.occurredAt = LocalDateTime.now();
    }
}
