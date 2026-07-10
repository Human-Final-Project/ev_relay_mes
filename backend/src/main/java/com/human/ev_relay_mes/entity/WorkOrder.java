package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "work_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "work_order_id")
    private Long workOrderId;

    @Column(name = "order_no", nullable = false, unique = true, length = 50)
    private String orderNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_code", referencedColumnName = "item_code", nullable = false)
    private Item item;

    @Column(name = "target_qty", nullable = false)
    private Integer targetQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.CREATED;

    @Column(name = "planned_start_at")
    private LocalDateTime plannedStartAt;

    @Column(name = "planned_end_at")
    private LocalDateTime plannedEndAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private Member createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public enum Status { CREATED, RELEASED, RUNNING, COMPLETED, CANCELED }
}
