package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inspections",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_inspection_event_id", columnNames = "event_id"),
                @UniqueConstraint(
                        name = "uk_inspection_unit_item",
                        columnNames = {"lot_no", "process_code", "unit_seq", "inspection_item"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inspection {

    @Column(name = "event_id", length = 100)
    private String eventId;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_code", referencedColumnName = "process_code", nullable = false)
    private Process process;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "snapshot_id", nullable = false)
    private LotInspectionStandardSnapshot standardSnapshot;

    @Column(name = "unit_seq", nullable = false)
    private Integer unitSeq;

    @Column(name = "inspection_item", nullable = false, length = 100)
    private String inspectionItem;

    @Column(name = "measured_value", nullable = false, precision = 12, scale = 3)
    private BigDecimal measuredValue;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "lower_limit", precision = 12, scale = 3)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 12, scale = 3)
    private BigDecimal upperLimit;

    @Column(name = "standard_version", nullable = false)
    private Integer standardVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false, length = 10)
    private Result result;

    @CreationTimestamp
    @Column(name = "inspected_at", nullable = false, updatable = false)
    private LocalDateTime inspectedAt;

    public enum Result { OK, NG }
}
