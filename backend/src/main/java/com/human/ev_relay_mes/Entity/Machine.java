package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "machines")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Machine {

    // PK가 EQ-WIND-01 같은 업무 코드라서 직접 세팅한다.
    @Id
    @Column(name = "machine_id", length = 50)
    private String machineId;

    @Column(name = "machine_name", nullable = false, length = 100)
    private String machineName;

    @Column(name = "machine_type", nullable = false, length = 30)
    private String machineType;

    // processes 테이블이 엔티티로 추가되어 실제 연관관계로 매핑한다.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_code", referencedColumnName = "process_code", nullable = false)
    private Process process;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.IDLE;

    @Column(name = "use_yn", nullable = false, length = 1)
    @Builder.Default
    private String useYn = "Y";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status { IDLE, RUNNING, ERROR, STOPPED }
}
