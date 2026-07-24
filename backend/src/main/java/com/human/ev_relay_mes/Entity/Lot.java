package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_id")
    private Long lotId;

    @Column(name = "lot_no", nullable = false, unique = true, length = 50)
    private String lotNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_order_id", nullable = false)
    private WorkOrder workOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_code", referencedColumnName = "item_code", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_process_code", referencedColumnName = "process_code")
    private Process currentProcess;

    @Enumerated(EnumType.STRING)
    @Column(name = "lot_type", nullable = false, length = 20)
    @Builder.Default
    private LotType lotType = LotType.INITIAL;

    @Column(name = "production_round", nullable = false)
    @Builder.Default
    private Integer productionRound = 1;

    @Column(name = "input_qty", nullable = false)
    private Integer inputQty;

    @Column(name = "ok_qty", nullable = false)
    @Builder.Default
    private Integer okQty = 0;

    @Column(name = "ng_qty", nullable = false)
    @Builder.Default
    private Integer ngQty = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.WAITING;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "start_requested_at")
    private LocalDateTime startRequestedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum LotType { INITIAL, SUPPLEMENT }

    public enum Status { WAITING, RUNNING, COMPLETED, HOLD, SCRAPPED }
}
