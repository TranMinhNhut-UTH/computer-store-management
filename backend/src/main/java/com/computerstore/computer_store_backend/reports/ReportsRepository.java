package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.reports.ReportsOverview.ProductCategoryCount;
import com.computerstore.computer_store_backend.reports.ReportsOverview.StatusCounts;
import com.computerstore.computer_store_backend.reports.InventoryReport.StockFilter;
import com.computerstore.computer_store_backend.reports.InventoryReport.StockProduct;
import com.computerstore.computer_store_backend.reports.InventoryReport.StockStatus;
import com.computerstore.computer_store_backend.reports.InventoryReport.Summary;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import org.springframework.data.domain.Pageable;
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

    InventoryReport inventory(Pageable page, StockFilter filter) {
        String filterPredicate = switch (filter) {
            case ALL -> "";
            case LOW -> "AND i.quantity_on_hand <= p.min_stock_level";
            case OUT -> "AND i.quantity_on_hand = 0";
        };
        // One consistent read supplies the summary, total matches, and the bounded page.
        return jdbc.query("""
                WITH totals AS (
                    SELECT COUNT(*) AS active_skus,
                           COUNT(*) - COUNT(i.product_id) AS missing_balances,
                           COALESCE(SUM(i.quantity_on_hand), 0) AS total_quantity,
                           COUNT(CASE WHEN i.quantity_on_hand = 0 THEN 1 END) AS out_of_stock,
                           COUNT(CASE WHEN i.quantity_on_hand <= p.min_stock_level THEN 1 END) AS low_stock
                    FROM product p LEFT JOIN inventory i ON i.product_id = p.id
                    WHERE p.is_active = TRUE
                ), stock_page AS (
                    SELECT p.id, p.sku, p.name AS product_name, c.name AS category_name,
                           i.quantity_on_hand, p.min_stock_level
                    FROM product p
                    JOIN inventory i ON i.product_id = p.id
                    JOIN category c ON c.id = p.category_id
                    WHERE p.is_active = TRUE %s
                    ORDER BY p.sku, p.id
                    LIMIT ? OFFSET ?
                )
                SELECT totals.*, stock_page.*
                FROM totals LEFT JOIN stock_page ON TRUE
                ORDER BY stock_page.sku, stock_page.id
                """.formatted(filterPredicate), rows -> {
            if (!rows.next()) throw new IllegalStateException("Inventory summary row is missing.");
            if (rows.getLong("missing_balances") > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_CONFLICT,
                        "One or more active products have no inventory balance.");
            }
            var summary = new Summary(rows.getLong("active_skus"), rows.getBigDecimal("total_quantity").toPlainString(),
                    rows.getLong("out_of_stock"), rows.getLong("low_stock"));
            long total = switch (filter) {
                case ALL -> summary.activeSkus();
                case LOW -> summary.lowStockSkus();
                case OUT -> summary.outOfStockSkus();
            };
            var items = new ArrayList<StockProduct>();
            do {
                String sku = rows.getString("sku");
                if (sku != null) {
                    long quantity = rows.getLong("quantity_on_hand");
                    long threshold = rows.getLong("min_stock_level");
                    items.add(new StockProduct(sku, rows.getString("product_name"), rows.getString("category_name"),
                            Long.toString(quantity), Long.toString(threshold), stockStatus(quantity, threshold)));
                }
            } while (rows.next());
            return new InventoryReport(summary, List.copyOf(items), page.getPageNumber(), page.getPageSize(),
                    total, Math.ceilDiv(total, page.getPageSize()));
        }, page.getPageSize(), page.getOffset());
    }

    private static StockStatus stockStatus(long quantity, long threshold) {
        if (quantity == 0) return StockStatus.OUT_OF_STOCK;
        return quantity <= threshold ? StockStatus.LOW_STOCK : StockStatus.NORMAL;
    }

    private static StatusCounts counts(ResultSet row) throws SQLException {
        return new StatusCounts(row.getLong("total"), row.getLong("active"), row.getLong("inactive"));
    }
}
