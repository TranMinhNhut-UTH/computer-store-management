package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
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
}
