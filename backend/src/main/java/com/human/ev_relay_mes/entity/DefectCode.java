package com.human.ev_relay_mes.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "defect_codes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DefectCode {

    // PK가 COIL_RESISTANCE_NG 같은 업무 코드라서 직접 세팅한다.
    @Id
    @Column(name = "defect_code", length = 50)
    private String defectCode;

    @Column(name = "defect_name", nullable = false, length = 100)
    private String defectName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "process_code", referencedColumnName = "process_code", nullable = false)
    private Process process;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "use_yn", nullable = false, length = 1)
    @Builder.Default
    private String useYn = "Y";

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
