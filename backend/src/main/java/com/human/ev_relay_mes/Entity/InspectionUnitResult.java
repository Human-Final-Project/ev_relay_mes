package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inspection_unit_results",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_inspection_unit_result",
                columnNames = {"lot_no", "process_code", "unit_seq"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InspectionUnitResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "unit_result_id")
    private Long unitResultId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_no", referencedColumnName = "lot_no", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_code", referencedColumnName = "process_code", nullable = false)
    private Process process;

    @Column(name = "unit_seq", nullable = false)
    private Integer unitSeq;

    @Enumerated(EnumType.STRING)
    @Column(name = "l1_result", length = 10)
    private Inspection.Result l1Result;

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_result", length = 10)
    private Inspection.Result measurementResult;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 10)
    private Inspection.Result result;

    @Enumerated(EnumType.STRING)
    @Column(name = "evaluation_status", nullable = false, length = 20)
    @Builder.Default
    private EvaluationStatus evaluationStatus = EvaluationStatus.PENDING;

    @Column(name = "evaluated_at")
    private LocalDateTime evaluatedAt;

    public enum EvaluationStatus { PENDING, COMPLETED }
}
