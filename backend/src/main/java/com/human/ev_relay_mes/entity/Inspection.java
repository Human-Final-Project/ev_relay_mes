package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "inspections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "inspection_id")
    private Long inspectionId;

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

    @Column(name = "inspection_item", nullable = false, length = 100)
    private String inspectionItem;

    @Column(name = "measured_value", precision = 12, scale = 3)
    private BigDecimal measuredValue;

    // 공정마다 측정 단위가 달라서(Ω, MPa, V 등) unit은 유지한다.
    @Column(name = "unit", length = 20)
    private String unit;

    @Column(name = "lower_limit", precision = 12, scale = 3)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 12, scale = 3)
    private BigDecimal upperLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private Result result;

    @CreationTimestamp
    @Column(name = "inspected_at", nullable = false, updatable = false)
    private LocalDateTime inspectedAt;

    public enum Result { OK, NG }
}
