package com.computerstore.repository;

import com.computerstore.dto.AuditLogDto;
import com.computerstore.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = """
            select new com.computerstore.dto.AuditLogDto(
                a.id, u.id, u.fullName, a.action, a.entityName, a.entityId,
                a.oldValues, a.newValues, a.ipAddress, a.createdAt)
            from AuditLog a left join a.user u
            where (:userId is null or u.id = :userId)
              and (:action is null or a.action = :action)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt <= :to)
            order by a.createdAt desc
            """,
            countQuery = """
            select count(a) from AuditLog a left join a.user u
            where (:userId is null or u.id = :userId)
              and (:action is null or a.action = :action)
              and (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt <= :to)
            """)
    Page<AuditLogDto> search(@Param("userId") Long userId,
                             @Param("action") String action,
                             @Param("from") Instant from,
                             @Param("to") Instant to,
                             Pageable pageable);
}
