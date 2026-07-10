package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "material_lots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MaterialLot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "material_lot_id")
    private Long materialLotId;

    @Column(name = "material_lot_no", nullable = false, unique = true, length = 50)
    private String materialLotNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_code", referencedColumnName = "item_code", nullable = false)
    private Item item;

    @Column(name = "received_qty", nullable = false)
    private Integer receivedQty;

    @Column(name = "current_qty", nullable = false)
    private Integer currentQty;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.AVAILABLE;

    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "received_by")
    private Member receivedBy;

    @PrePersist
    public void prePersist() {
        this.receivedAt = LocalDateTime.now();
    }

    public enum Status { AVAILABLE, HOLD, USED, DISCARDED }
}
