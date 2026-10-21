package com.computerstore.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "audit_logs",
        indexes = {
                @Index(name = "idx_audit_created", columnList = "createdAt"),
                @Index(name = "idx_audit_user", columnList = "user_id")
        })
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String action;

    @Column(length = 100)
    private String entityName;

    @Column(length = 50)
    private String entityId;

    @Column(length = 4000)
    private String oldValues;

    @Column(length = 4000)
    private String newValues;

    @Column(length = 45)
    private String ipAddress;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
