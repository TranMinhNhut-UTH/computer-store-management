package com.computerstore.computer_store_backend.partners.entity;

import com.computerstore.computer_store_backend.shared.persistence.AbstractEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(name = "customer",
        uniqueConstraints = @UniqueConstraint(name = "uq_customer_code", columnNames = "code"),
        indexes = {
                @Index(name = "ix_customer_phone", columnList = "phone"),
                @Index(name = "ix_customer_full_name", columnList = "full_name")
        })
@Check(name = "ck_customer_active", constraints = "is_active IN (0, 1)")
public class Customer extends AbstractEntity {

    @Column(name = "code", nullable = false, length = 50,
            columnDefinition = "VARCHAR(50) COLLATE utf8mb4_0900_bin")
    private String code;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "phone", nullable = true, length = 30)
    private String phone;

    @Column(name = "email", nullable = true, length = 254)
    private String email;

    @Column(name = "address", nullable = true, length = 500)
    private String address;

    @Column(name = "notes", nullable = true, columnDefinition = "TEXT")
    private String notes;

    @Column(name = "is_active", nullable = false, columnDefinition = "BOOLEAN")
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Customer() {
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
