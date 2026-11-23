package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.reports.ReportsOverview.ProductCategoryCount;
import com.computerstore.computer_store_backend.reports.ReportsOverview.StatusCounts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReportsRepository {
    private final JdbcTemplate jdbc;

    public ReportsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    ReportsOverview overview() {
        var summary = jdbc.query("""
                SELECT 'product' AS metric, COUNT(*) AS total, COALESCE(SUM(is_active = TRUE), 0) AS active,
                       COALESCE(SUM(is_active = FALSE), 0) AS inactive FROM product
                UNION ALL
                SELECT 'category', COUNT(*), COALESCE(SUM(is_active = TRUE), 0),
                       COALESCE(SUM(is_active = FALSE), 0) FROM category
                UNION ALL
                SELECT 'supplier', COUNT(*), COALESCE(SUM(is_active = TRUE), 0),
                       COALESCE(SUM(is_active = FALSE), 0) FROM supplier
                UNION ALL
                SELECT 'customer', COUNT(*), COALESCE(SUM(is_active = TRUE), 0),
                       COALESCE(SUM(is_active = FALSE), 0) FROM customer
                """, rows -> {
            var counts = new HashMap<String, StatusCounts>();
            while (rows.next()) counts.put(rows.getString("metric"), counts(rows));
            return counts;
        });
        var grouped = jdbc.query("""
                SELECT c.code, c.name, COUNT(p.id) AS total, COALESCE(SUM(p.is_active = TRUE), 0) AS active,
                       COALESCE(SUM(p.is_active = FALSE), 0) AS inactive
                FROM category c LEFT JOIN product p ON p.category_id = c.id
                GROUP BY c.code, c.name
                ORDER BY c.code
                """, (row, number) -> new ProductCategoryCount(row.getString("code"), row.getString("name"), counts(row)));
        return new ReportsOverview(summary.get("product"), summary.get("category"), summary.get("supplier"),
                summary.get("customer"), List.copyOf(grouped));
    }

    private static StatusCounts counts(ResultSet row) throws SQLException {
        return new StatusCounts(row.getLong("total"), row.getLong("active"), row.getLong("inactive"));
    }
}
