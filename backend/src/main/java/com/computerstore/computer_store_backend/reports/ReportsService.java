package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.pagination.ListPagination;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;

@Service
public class ReportsService {
    private static final Set<String> OVERVIEW_READS =
            Set.of(PRODUCT_READ.code(), CATEGORY_READ.code(), SUPPLIER_READ.code(), CUSTOMER_READ.code());

    private final RoleResolutionService roles;
    private final ReportsRepository reports;

    public ReportsService(RoleResolutionService roles, ReportsRepository reports) {
        this.roles = roles;
        this.reports = reports;
    }

    public void requireOverviewAccess() {
        var access = roles.resolveCurrent();
        if (!access.roles().contains(EmployeeRole.MANAGER) || !access.permissionCodes().containsAll(OVERVIEW_READS)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED, ErrorCode.ACCESS_DENIED.detail());
        }
    }

    @Transactional(readOnly = true)
    public ReportsOverview overview() {
        requireOverviewAccess();
        return reports.overview();
    }

    @Transactional(readOnly = true)
    public InventoryReport inventory(int page, int size, InventoryReport.StockFilter filter) {
        requireOverviewAccess();
        var pagination = ListPagination.request(page, size, "sku",
                () -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid report pagination."));
        return reports.inventory(pagination, filter);
    }

    @Transactional(readOnly = true)
    public ProductStockReport productStock(String productId) {
        requireOverviewAccess();
        if (!productId.matches("[1-9][0-9]{0,18}")) throw invalidProductId();
        long id;
        try { id = Long.parseLong(productId); }
        catch (NumberFormatException ignored) { throw invalidProductId(); }
        return reports.productStock(id);
    }

    private static BusinessException invalidProductId() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid product id.");
    }
}
