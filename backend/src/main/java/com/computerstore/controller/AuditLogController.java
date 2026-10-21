package com.computerstore.controller;

import com.computerstore.dto.AuditLogDto;
import com.computerstore.dto.PagedResult;
import com.computerstore.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** FR-06 – Xem nhật ký thao tác (chỉ Quản lý). */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('QUAN_LY')")
public class AuditLogController {

    private final AuditLogRepository repo;

    @GetMapping
    public PagedResult<AuditLogDto> list(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {

        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 100);
        Page<AuditLogDto> result = repo.search(userId, StringUtils.hasText(action) ? action : null,
                from, to, PageRequest.of(p - 1, s));
        return new PagedResult<>(result.getContent(), result.getTotalElements(), p, s);
    }
}
