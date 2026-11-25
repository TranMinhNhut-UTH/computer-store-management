package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.controller.CategoryController;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.service.CategoryService;
import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.*;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real filter/controller/service/actor/resolver/policy, mock persistence; no Boot/JDBC/Flyway. */
@SpringJUnitConfig(CategorySecurityTest.Fixture.class)
@WebAppConfiguration
class CategorySecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired CategoryRepository categories;
    @Autowired AppUserRepository users;
    @Autowired EntityManager entities;
    private TypedQuery<String> roles, permissions;
    private AppUser employee;
    private EmployeePrincipal principal;
    private MockMvc mvc;
    private static final String BASE = "/api/v1/categories";
    private static final String BODY = "{\"code\":\"CPU\",\"name\":\"Processors\",\"description\":null}";

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        reset(categories, users, entities);
        roles = mock(TypedQuery.class, RETURNS_SELF);
        permissions = mock(TypedQuery.class, RETURNS_SELF);
        when(entities.createQuery(contains("select distinct r.code"), eq(String.class))).thenReturn(roles);
        when(entities.createQuery(contains("select distinct p.code"), eq(String.class))).thenReturn(permissions);
        when(roles.getResultList()).thenReturn(List.of("MANAGER"));
        when(permissions.getResultList()).thenReturn(List.of());
        employee = mock(AppUser.class);
        when(employee.getId()).thenReturn(42L);
        when(employee.getUsername()).thenReturn("employee");
        when(employee.getPasswordHash()).thenReturn("test-only-not-a-real-credential");
        when(employee.getActive()).thenReturn(true);
        when(users.findByUsername("employee")).thenReturn(Optional.of(employee));
        when(users.findById(42L)).thenReturn(Optional.of(employee));
        principal = new EmployeeUserDetailsService(users).loadUserByUsername("employee");
        principal.eraseCredentials();
        when(categories.search(isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(CategoryTestData.category(7, true))));
        when(categories.findById(7L)).thenReturn(Optional.of(CategoryTestData.category(7, true)));
        when(categories.findForUpdate(7L)).thenAnswer(call -> Optional.of(CategoryTestData.category(7, true)));
        when(categories.findProductReferencesForDeactivation(eq(7L), any())).thenReturn(List.of());
        when(categories.findByCode(anyString())).thenReturn(Optional.empty());
        when(categories.saveAndFlush(any(Category.class))).thenAnswer(call -> {
            Category category = call.getArgument(0);
            if (category.getId() == null) ReflectionTestUtils.setField(category, "id", 7L);
            return category;
        });
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private List<MockHttpServletRequestBuilder> operations() {
        return List.of(get(BASE), get(BASE + "/7"),
                post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY),
                put(BASE + "/7").contentType(MediaType.APPLICATION_JSON).content(BODY), post(BASE + "/7/deactivate"));
    }

    @Test void anonymousCannotReachAnyCategoryRouteEvenWithValidCsrf() throws Exception {
        for (var operation : operations()) mvc.perform(operation.with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(categories, entities);
        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
    }

    @Test void authenticatedWithoutRuntimeGrantsIs403BeforeCategoryPersistence() throws Exception {
        for (var operation : operations()) mvc.perform(operation.with(user(principal)).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(categories);
        verify(entities, atLeastOnce()).createQuery(contains("select distinct p.code"), eq(String.class));
    }

    @Test void managerWithServerMappingsReachesAllFiveCategoryOperations() throws Exception {
        when(permissions.getResultList()).thenReturn(List.of("CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE"));
        int[] statuses = {200, 200, 201, 200, 200};
        var operations = operations();
        for (int i = 0; i < operations.size(); i++) mvc.perform(operations.get(i).with(user(principal)).with(csrf()))
                .andExpect(status().is(statuses[i]));
        verify(categories).findById(7L);
        verify(categories, times(3)).saveAndFlush(any());
        verify(permissions, times(5)).setParameter("userId", 42L);
    }

    @ParameterizedTest @EnumSource(value = EmployeeRole.class, names = {"SALES_STAFF", "WAREHOUSE_STAFF"})
    void readOnlyMappingsPermitReadsButNotMutations(EmployeeRole role) throws Exception {
        when(roles.getResultList()).thenReturn(List.of(role.name()));
        when(permissions.getResultList()).thenReturn(List.of("CATEGORY_READ"));
        var operations = operations();
        for (int i = 0; i < operations.size(); i++) mvc.perform(operations.get(i).with(user(principal)).with(csrf()))
                .andExpect(status().is(i < 2 ? 200 : 403));
        verify(categories, never()).saveAndFlush(any());
    }

    @ParameterizedTest @EnumSource(value = PermissionCode.class,
            names = {"CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE"})
    void eachOperationRequiresItsOwnServerGrant(PermissionCode granted) throws Exception {
        when(permissions.getResultList()).thenReturn(List.of(granted.code()));
        var operations = operations();
        for (int i = 0; i < operations.size(); i++) {
            boolean allowed = granted == PermissionCode.CATEGORY_READ ? i < 2
                    : granted == PermissionCode.CATEGORY_CREATE ? i == 2
                    : granted == PermissionCode.CATEGORY_UPDATE ? i == 3 : i == 4;
            mvc.perform(operations.get(i).with(user(principal)).with(csrf()))
                    .andExpect(status().is(allowed ? (i == 2 ? 201 : 200) : 403));
        }
        verify(categories, times(granted == PermissionCode.CATEGORY_READ ? 0 : 1)).saveAndFlush(any());
    }

    @Test void missingCsrfDeniesEveryMutationAndInvalidCsrfDeniesCreateBeforePermissionOrPersistence() throws Exception {
        when(permissions.getResultList()).thenReturn(List.of("CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE"));
        for (var operation : operations().subList(2, 5)) mvc.perform(operation.with(user(principal)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY).with(user(principal)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categories, entities);
    }

    @Test void invalidCsrfDeniesUpdateBeforePermissionOrPersistence() throws Exception {
        when(permissions.getResultList()).thenReturn(List.of("CATEGORY_UPDATE"));
        mvc.perform(put(BASE + "/7").contentType(MediaType.APPLICATION_JSON).content(BODY)
                        .with(user(principal)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(categories, entities);
    }

    @Test void invalidCsrfDeniesDeactivateBeforePermissionOrPersistence() throws Exception {
        when(permissions.getResultList()).thenReturn(List.of("CATEGORY_DEACTIVATE"));
        mvc.perform(post(BASE + "/7/deactivate").with(user(principal)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(categories, entities);
    }

    @Test void adminRoleAndForgedPrincipalAuthoritiesDoNotGrantCategoryAccess() throws Exception {
        when(roles.getResultList()).thenReturn(List.of("ADMIN"));
        for (var operation : operations()) mvc.perform(operation.with(user(principal)).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(get(BASE).with(user("forged").authorities(() -> "CATEGORY_READ", () -> "ROLE_ADMIN")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(categories);
    }

    @Test void provisionalAndWildcardMappingsCannotSubstituteForTheCategoryGrant() throws Exception {
        when(permissions.getResultList()).thenReturn(List.of("ROLE_READ", "PERMISSION_READ", "CUSTOMER_DEACTIVATE",
                "ADJUSTMENT_REJECT", "ADJUSTMENT_CANCEL", "SALE_COMPLETE", "PAYMENT_VOID", "REFUND_READ", "REFUND_CREATE", "*", "CATEGORY_*"));
        for (var operation : operations()) mvc.perform(operation.with(user(principal)).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(categories);
    }

    @Test void accountMustStillBeActiveOnServerForEveryRequest() throws Exception {
        when(employee.getActive()).thenReturn(false);
        when(permissions.getResultList()).thenReturn(List.of("CATEGORY_READ"));
        mvc.perform(get(BASE).with(user(principal))).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(categories, entities);
    }

    @Test void allOtherMethodsPathsAndBusinessApisRemainDeniedEvenWithCategoryGrants() throws Exception {
        when(permissions.getResultList()).thenReturn(List.of("CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE"));
        for (var request : List.of(delete(BASE + "/7"), patch(BASE + "/7"), post(BASE + "/7/reactivate"),
                get(BASE + "/7/deactivate"), get(BASE + "/7/products"), post(BASE + "/bulk"),
                put(BASE), head(BASE), options(BASE), get("/api/v1/inventory"), post("/api/v1/sales"))) {
            mvc.perform(request.with(user(principal)).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(categories, entities);
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class,
            EmployeeUserDetailsService.class, CategoryController.class, CategoryService.class, GlobalExceptionHandler.class,
            CurrentActorService.class, RoleResolutionService.class, AuthorizationService.class, PermissionPolicy.class})
    static class Fixture {
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
        @Bean CategoryRepository categories() { return mock(CategoryRepository.class); }
        @Bean EntityManager entities() { return mock(EntityManager.class); }
        @Bean UtcTimeProvider time() { return new UtcTimeProvider(Clock.systemUTC()); }
    }
}
