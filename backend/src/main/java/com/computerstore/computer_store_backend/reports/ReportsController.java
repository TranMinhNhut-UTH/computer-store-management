package com.computerstore.computer_store_backend.reports;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {
    private final ReportsService reports;
    private final InventoryMovementService movements;

    public ReportsController(ReportsService reports, InventoryMovementService movements) {
        this.reports = reports;
        this.movements = movements;
    }

    @GetMapping("/access")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void access() {
        reports.requireOverviewAccess();
    }

    @GetMapping("/overview")
    public ReportsOverview overview() {
        return reports.overview();
    }

    @GetMapping("/inventory")
    public InventoryReport inventory(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "LOW") InventoryReport.StockFilter filter) {
        return reports.inventory(page, size, filter);
    }

    @GetMapping("/inventory/movements")
    public InventoryMovementPage movements(@RequestParam(required = false) String q,
            @RequestParam(required = false) String type, @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return movements.movements(q, type, fromDate, toDate, page, size);
    }
}
