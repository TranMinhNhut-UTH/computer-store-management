package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.inventory.entity.InventoryTransactionType;
import com.computerstore.computer_store_backend.inventory.repository.InventoryTransactionRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.pagination.ListPagination;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryMovementService {
    private static final ZoneId REPORT_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalDateTime MYSQL_MIN_DATETIME = LocalDate.of(1000, 1, 1).atStartOfDay();
    private final ReportsService reports;
    private final InventoryTransactionRepository transactions;

    public InventoryMovementService(ReportsService reports, InventoryTransactionRepository transactions) {
        this.reports = reports;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InventoryMovementPage movements(String query, String type, String fromDate, String toDate,
            int page, int size) {
        reports.requireOverviewAccess();
        var pagination = ListPagination.request(page, size, "occurredAt",
                () -> invalid("Invalid report pagination."))
                .withSort(Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        var pattern = searchPattern(query);
        var movementType = movementType(type);
        var from = parseDate(fromDate);
        var to = parseDate(toDate);
        if (from != null && to != null && from.isAfter(to)) {
            throw invalid("The start date must be on or before the end date.");
        }
        var untilUtc = to == null ? null : utcMidnight(to.plusDays(1));
        if (untilUtc != null && !untilUtc.isAfter(MYSQL_MIN_DATETIME)) {
            return new InventoryMovementPage(List.of(), page, size, 0, 0);
        }
        var fromUtc = from == null ? null : utcMidnight(from);
        if (fromUtc != null && fromUtc.isBefore(MYSQL_MIN_DATETIME)) fromUtc = null;

        var result = transactions.findMovements(pattern, movementType, fromUtc, untilUtc, pagination);
        var rows = result.getContent().stream().map(row -> new InventoryMovementPage.Movement(
                row.getSku(), row.getProductName(), row.getMovementType(), Long.toString(row.getQuantityDelta()),
                row.getOccurredAt().toInstant(ZoneOffset.UTC))).toList();
        return new InventoryMovementPage(rows, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    private static String searchPattern(String query) {
        if (query == null || query.isBlank()) return null;
        var term = query.strip();
        if (term.codePointCount(0, term.length()) > 200) throw invalid("Product search is too long.");
        return "%" + term.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static InventoryTransactionType movementType(String value) {
        if (value == null) return null;
        try { return InventoryTransactionType.valueOf(value); }
        catch (IllegalArgumentException ignored) { throw invalid("Invalid movement type."); }
    }

    private static LocalDate parseDate(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000")) {
            throw invalid("Dates must be valid calendar dates in YYYY-MM-DD format.");
        }
        try { return LocalDate.parse(value); }
        catch (DateTimeParseException ignored) { throw invalid("Dates must be valid calendar dates in YYYY-MM-DD format."); }
    }

    private static LocalDateTime utcMidnight(LocalDate date) {
        return date.atStartOfDay(REPORT_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    private static BusinessException invalid(String detail) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, detail);
    }
}
