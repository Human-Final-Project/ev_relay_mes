package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "lot_inspection_standard_snapshots",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lot_inspection_snapshot",
                columnNames = {"lot_no", "process_code", "inspection_item"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotInspectionStandardSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "snapshot_id")
    private Long snapshotId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_no", referencedColumnName = "lot_no", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_code", referencedColumnName = "process_code", nullable = false)
    private Process process;

    @Column(name = "inspection_item", nullable = false, length = 100)
    private String inspectionItem;

    @Column(name = "item_name", nullable = false, length = 100)
    private String itemName;

    @Column(name = "unit", nullable = false, length = 20)
    private String unit;

    @Column(name = "lower_limit", precision = 12, scale = 3)
    private BigDecimal lowerLimit;

    @Column(name = "upper_limit", precision = 12, scale = 3)
    private BigDecimal upperLimit;

    @Column(name = "standard_version", nullable = false)
    private Integer standardVersion;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private LocalDateTime capturedAt;
}
