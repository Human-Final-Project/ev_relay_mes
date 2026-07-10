package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

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

    // processes 테이블이 엔티티로 추가되어 실제 연관관계로 매핑한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_process_code", referencedColumnName = "process_code")
    private Process currentProcess;

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

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public enum Status { WAITING, RUNNING, COMPLETED, HOLD, SCRAPPED }
}
