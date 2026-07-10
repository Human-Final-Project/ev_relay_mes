package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Process {

    // PK가 OP20 같은 업무 코드라서 직접 세팅한다.
    @Id
    @Column(name = "process_code", length = 30)
    private String processCode;

    @Column(name = "process_name", nullable = false, length = 100)
    private String processName;

    @Column(name = "process_order", nullable = false)
    private Integer processOrder;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "use_yn", nullable = false, length = 1)
    @Builder.Default
    private String useYn = "Y";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
