package com.computerstore.computer_store_backend.reports;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
public class ReportsController {
    private final ReportsService reports;

    public ReportsController(ReportsService reports) {
        this.reports = reports;
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
}
