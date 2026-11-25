package com.computerstore.computer_store_backend.identity.entity;

import com.computerstore.computer_store_backend.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user", uniqueConstraints = {
        @UniqueConstraint(name = "uq_app_user_username", columnNames = "username"),
        @UniqueConstraint(name = "uq_app_user_email", columnNames = "email")
})
@Check(name = "ck_app_user_active", constraints = "is_active IN (0, 1)")
public class AppUser extends AbstractEntity {

    @Column(name = "username", nullable = false, length = 100,
            columnDefinition = "VARCHAR(100) COLLATE utf8mb4_0900_bin")
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email", length = 254,
            columnDefinition = "VARCHAR(254) COLLATE utf8mb4_0900_bin")
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN")
    private Boolean active = true;

    @Column(name = "session_version", nullable = false)
    private long sessionVersion;

    @Column(name = "last_login_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected AppUser() {
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public long getSessionVersion() { return sessionVersion; }
    public void revokeSessions() { sessionVersion = Math.incrementExact(sessionVersion); }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
