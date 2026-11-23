package com.computerstore.computer_store_backend.identity.controller;

import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;

@RestController
public class UiCapabilitiesController {
    private static final List<PermissionCode> UI_CODES = List.of(
            CATEGORY_READ, CATEGORY_CREATE, CATEGORY_UPDATE, CATEGORY_DEACTIVATE,
            PRODUCT_READ, PRODUCT_CREATE, PRODUCT_PRICE_UPDATE, SUPPLIER_READ, CUSTOMER_READ);
    private final RoleResolutionService roles;

    public UiCapabilitiesController(RoleResolutionService roles) {
        this.roles = roles;
    }

    @GetMapping("/api/v1/auth/ui-capabilities")
    public UiCapabilities capabilities() {
        var permissions = roles.resolveCurrent().permissionCodes();
        return new UiCapabilities(UI_CODES.stream()
                .filter(code -> code.status() == PermissionCode.Status.ELIGIBLE_PROPOSED)
                .map(PermissionCode::code)
                .filter(permissions::contains)
                .toList());
    }

    public record UiCapabilities(List<String> permissions) { }
}
