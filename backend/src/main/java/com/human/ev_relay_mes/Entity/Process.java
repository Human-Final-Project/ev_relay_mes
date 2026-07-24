package com.human.ev_relay_mes.Entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
