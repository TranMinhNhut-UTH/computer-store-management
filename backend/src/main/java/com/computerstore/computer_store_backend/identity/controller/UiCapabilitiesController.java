package com.computerstore.computer_store_backend.identity.controller;

import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;

@RestController
public class UiCapabilitiesController {
    private static final List<PermissionCode> UI_CODES = List.of(
            CATEGORY_READ, CATEGORY_CREATE, CATEGORY_UPDATE, CATEGORY_DEACTIVATE,
            PRODUCT_READ, PRODUCT_CREATE, PRODUCT_PRICE_UPDATE, SUPPLIER_READ, CUSTOMER_READ);
    private final RoleResolutionService roles;
    private final boolean staffManagement;

    public UiCapabilitiesController(RoleResolutionService roles,
            @Value("${csm.staff-management.enabled:false}") boolean staffManagement) {
        this.roles = roles;
        this.staffManagement = staffManagement;
    }

    @GetMapping("/api/v1/auth/ui-capabilities")
    public UiCapabilities capabilities() {
        var access = roles.resolveCurrent();
        var codes = staffManagement && access.roles().contains(EmployeeRole.ADMIN)
                ? Stream.concat(UI_CODES.stream(), Stream.of(EMPLOYEE_READ)) : UI_CODES.stream();
        return new UiCapabilities(codes
                .filter(code -> code.status() == PermissionCode.Status.ELIGIBLE_PROPOSED)
                .map(PermissionCode::code)
                .filter(access.permissionCodes()::contains)
                .toList());
    }

    public record UiCapabilities(List<String> permissions) { }
}
