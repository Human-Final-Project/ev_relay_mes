package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inspection_standards",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inspection_standard_process_item",
                columnNames = {"process_code", "inspection_item"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionStandard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "standard_id")
    private Long standardId;

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
    @Builder.Default
    private Integer standardVersion = 1;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
