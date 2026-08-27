package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "machine_worker_assignments",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_machine_worker_assignment",
                columnNames = {"machine_id", "worker_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MachineWorkerAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "assignment_id")
    private Long assignmentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "machine_id", nullable = false)
    private Machine machine;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "worker_id", nullable = false)
    private Worker worker;

    @Enumerated(EnumType.STRING)
    @Column(name = "assignment_role", nullable = false, length = 20)
    private AssignmentRole assignmentRole;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    public enum AssignmentRole {
        RESPONSIBLE,
        WORKER
    }
}
