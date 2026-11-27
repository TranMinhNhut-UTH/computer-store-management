package com.computerstore.computer_store_backend.reports;

import com.computerstore.computer_store_backend.identity.authorization.CurrentActor;
import com.computerstore.computer_store_backend.identity.authorization.EmployeeRole;
import com.computerstore.computer_store_backend.identity.authorization.ResolvedAccess;
import com.computerstore.computer_store_backend.identity.authorization.RoleResolutionService;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.ApiAccessDeniedHandler;
import com.computerstore.computer_store_backend.identity.security.ApiAuthenticationEntryPoint;
import com.computerstore.computer_store_backend.identity.security.EmployeeUserDetailsService;
import com.computerstore.computer_store_backend.identity.security.SecurityConfig;
import com.computerstore.computer_store_backend.reports.ReportsOverview.StatusCounts;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static com.computerstore.computer_store_backend.reports.InventoryReport.StockFilter.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ReportsSecurityTest.Fixture.class)
@WebAppConfiguration
class ReportsSecurityTest {
    private static final String BASE = "/api/v1/reports";
    private static final Set<String> READS = Set.of("PRODUCT_READ", "CATEGORY_READ", "SUPPLIER_READ", "CUSTOMER_READ");
    private static final List<String> PATHS = List.of("/access", "/overview", "/inventory", "/inventory/movements");

    @Autowired WebApplicationContext context;
    @Autowired RoleResolutionService roles;
    @Autowired ReportsRepository reports;
    private MockMvc mvc;

    @BeforeEach void setup() {
        reset(roles, reports);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void anonymousCannotReadReportRoutes() throws Exception {
        for (String path : PATHS) {
            mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(roles, reports);
    }

    @Test void managerNeedsEveryExistingReadGrantAndOtherRolesAreDenied() throws Exception {
        resolved(EmployeeRole.MANAGER, Set.of("PRODUCT_READ", "CATEGORY_READ", "SUPPLIER_READ"));
        mvc.perform(get(BASE + "/overview").with(user("fixture"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/inventory").with(user("fixture"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/inventory").param("filter", "ALL").with(user("fixture")))
                .andExpect(status().isForbidden());
        for (var role : List.of(EmployeeRole.ADMIN, EmployeeRole.SALES_STAFF, EmployeeRole.WAREHOUSE_STAFF)) {
            resolved(role, READS);
            for (String path : PATHS) {
                mvc.perform(get(BASE + path).with(user("fixture").roles("MANAGER")))
                        .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            }
        }
        verifyNoInteractions(reports);
    }

    @Test void managerReadsInventoryWithExistingGrantsAndBoundedPagination() throws Exception {
        resolved(EmployeeRole.MANAGER, READS);
        var summary = new InventoryReport.Summary(1, "9007199254740993", 0, 1);
        var row = new InventoryReport.StockProduct("SKU", "Product", "Category",
                "9007199254740993", "9007199254740993", InventoryReport.StockStatus.LOW_STOCK);
        var inventory = new InventoryReport(summary, List.of(row), 0, 20, 1, 1);
        when(reports.inventory(any(), any())).thenReturn(inventory);
        mvc.perform(get(BASE + "/inventory").with(user("fixture"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.totalQuantity").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].minStockLevel").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].stockStatus").value("LOW_STOCK"))
                .andExpect(jsonPath("$.items[0].sellingPrice").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(reports).inventory(argThat(page -> page.getPageNumber() == 0 && page.getPageSize() == 20), eq(LOW));
        for (var filter : List.of(ALL, OUT)) {
            mvc.perform(get(BASE + "/inventory").param("filter", filter.name()).with(user("fixture")))
                    .andExpect(status().isOk());
            verify(reports).inventory(any(), eq(filter));
        }
        for (String invalid : List.of("?page=-1", "?size=101", "?size=0", "?page=2147483647&size=2", "?filter=UNKNOWN")) {
            mvc.perform(get(BASE + "/inventory" + invalid).with(user("fixture")))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verifyNoMoreInteractions(reports);
    }

    @Test void accessCheckDoesNotQueryReportsAndManagerSeesOnlyAggregates() throws Exception {
        resolved(EmployeeRole.MANAGER, READS);
        var none = new StatusCounts(0, 0, 0);
        mvc.perform(get(BASE + "/access").with(user("fixture"))).andExpect(status().isNoContent());
        verifyNoInteractions(reports);
        when(reports.overview()).thenReturn(new ReportsOverview(none, none, none, new StatusCounts(1, 0, 1), List.of()));
        mvc.perform(get(BASE + "/overview").with(user("fixture")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.customers.inactive").value(1))
                .andExpect(jsonPath("$.customers.contact").doesNotHaveJsonPath())
                .andExpect(jsonPath("$.revenue").doesNotHaveJsonPath());
        verify(reports).overview();
    }

    @Test void reportWritesAndUnreleasedReportsRemainDenied() throws Exception {
        mvc.perform(post(BASE + "/overview").with(user("fixture")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(BASE + "/inventory").with(user("fixture")).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/inventory/history").with(user("fixture"))).andExpect(status().isForbidden());
        verifyNoInteractions(roles, reports);
    }

    private void resolved(EmployeeRole role, Set<String> permissions) {
        when(roles.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(1L, "fixture"), Set.of(role), permissions));
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
            EmployeeUserDetailsService.class, GlobalExceptionHandler.class, ReportsService.class, ReportsController.class,
            InventoryMovementService.class})
    static class Fixture {
        @Bean RoleResolutionService roles() { return mock(RoleResolutionService.class); }
        @Bean ReportsRepository reports() { return mock(ReportsRepository.class); }
        @Bean InventoryMovementReportRepository transactions() { return mock(InventoryMovementReportRepository.class); }
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
    }
}
