package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.reports.ReportsOverview.ProductCategoryCount;
import com.computerstore.computer_store_backend.reports.ReportsOverview.StatusCounts;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
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
        // A single statement gives totals and the category breakdown the same MySQL read view.
        return jdbc.query("""
                SELECT 'product' AS metric, NULL AS category_code, NULL AS category_name,
                       COUNT(*) AS total, COALESCE(SUM(is_active = TRUE), 0) AS active,
                       COALESCE(SUM(is_active = FALSE), 0) AS inactive FROM product
                UNION ALL
                SELECT 'category', NULL, NULL, COUNT(*), COALESCE(SUM(is_active = TRUE), 0),
                       COALESCE(SUM(is_active = FALSE), 0) FROM category
                UNION ALL
                SELECT 'supplier', NULL, NULL, COUNT(*), COALESCE(SUM(is_active = TRUE), 0),
                       COALESCE(SUM(is_active = FALSE), 0) FROM supplier
                UNION ALL
                SELECT 'customer', NULL, NULL, COUNT(*), COALESCE(SUM(is_active = TRUE), 0),
                       COALESCE(SUM(is_active = FALSE), 0) FROM customer
                UNION ALL
                SELECT 'product_by_category', c.code, c.name, COUNT(p.id), COALESCE(SUM(p.is_active = TRUE), 0),
                       COALESCE(SUM(p.is_active = FALSE), 0)
                FROM category c LEFT JOIN product p ON p.category_id = c.id
                GROUP BY c.code, c.name
                ORDER BY category_code
                """, rows -> {
            var summary = new HashMap<String, StatusCounts>();
            var grouped = new ArrayList<ProductCategoryCount>();
            while (rows.next()) {
                String metric = rows.getString("metric");
                if ("product_by_category".equals(metric)) {
                    grouped.add(new ProductCategoryCount(rows.getString("category_code"),
                            rows.getString("category_name"), counts(rows)));
                } else {
                    summary.put(metric, counts(rows));
                }
            }
            return new ReportsOverview(summary.get("product"), summary.get("category"), summary.get("supplier"),
                    summary.get("customer"), List.copyOf(grouped));
        });
    }

    private static StatusCounts counts(ResultSet row) throws SQLException {
        return new StatusCounts(row.getLong("total"), row.getLong("active"), row.getLong("inactive"));
    }
}
