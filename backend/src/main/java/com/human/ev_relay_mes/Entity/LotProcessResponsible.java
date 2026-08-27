package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "lot_process_responsibles",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_lot_process_responsible",
                columnNames = {"lot_id", "process_code"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LotProcessResponsible {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "lot_process_responsible_id")
    private Long lotProcessResponsibleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "process_code", nullable = false)
    private Process process;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @CreationTimestamp
    @Column(name = "captured_at", nullable = false, updatable = false)
    private LocalDateTime capturedAt;
}
