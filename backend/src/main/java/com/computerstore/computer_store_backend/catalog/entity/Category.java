package com.computerstore.computer_store_backend.catalog.entity;

import com.computerstore.computer_store_backend.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "category",
        uniqueConstraints = @UniqueConstraint(name = "uq_category_code", columnNames = "code"),
        indexes = @Index(name = "ix_category_active_name", columnList = "is_active, name"))
@Check(name = "ck_category_active", constraints = "is_active IN (0, 1)")
public class Category extends AbstractEntity {

    @Column(name = "code", nullable = false, length = 50,
            columnDefinition = "VARCHAR(50) COLLATE utf8mb4_0900_bin")
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "description", nullable = true, length = 500)
    private String description;

    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN")
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Category() {
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
