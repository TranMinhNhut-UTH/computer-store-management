package com.computerstore.computer_store_backend.identity.entity;

import com.computerstore.computer_store_backend.shared.persistence.AbstractEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Column;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_role", uniqueConstraints =
        @UniqueConstraint(name = "uq_user_role_user_role", columnNames = {"user_id", "role_id"}), indexes =
        @Index(name = "ix_user_role_role", columnList = "role_id"))
public class UserRole extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_role_user"))
    private AppUser user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_user_role_role"))
    private AppRole role;

    @Column(name = "assigned_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime assignedAt;

    protected UserRole() {
    }

    public AppUser getUser() { return user; }
    public void setUser(AppUser user) { this.user = user; }
    public AppRole getRole() { return role; }
    public void setRole(AppRole role) { this.role = role; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public void setAssignedAt(LocalDateTime assignedAt) { this.assignedAt = assignedAt; }
}
