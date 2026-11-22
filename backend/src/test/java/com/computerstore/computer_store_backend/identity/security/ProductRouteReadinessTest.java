package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.catalog.controller.ProductController;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.repository.ProductRepository;
import com.computerstore.computer_store_backend.catalog.service.ProductService;
import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.identity.authorization.registration.ProductPermissionRollout;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Runs the candidate filter rules with the real actor, resolver, policy and Product service. */
@SpringJUnitConfig(ProductRouteReadinessTest.CandidateFixture.class)
@WebAppConfiguration
class ProductRouteReadinessTest {
    private static final String BASE = "/api/v1/products";
    private static final String INFO = """
            {"sku":"CPU","name":"Processor","categoryId":"7","minStockLevel":"0"}
            """;
    private static final String CREATE = "{\"information\":" + INFO + ",\"sellingPrice\":\"100\"}";
    private static final String PRICE = "{\"sellingPrice\":\"200\"}";
    private static final EmployeePrincipal PRINCIPAL = new EmployeePrincipal(42L, "fixture", null, true);

    @Autowired WebApplicationContext context;
    @Autowired AppUserRepository users;
    @Autowired EntityManager entities;
    @Autowired ProductRepository products;
    @Autowired CategoryRepository categories;
    private TypedQuery<String> roleQuery, permissionQuery;
    private MockMvc mvc;

    @BeforeEach @SuppressWarnings("unchecked") void setup() {
        reset(users, entities, products, categories);
        roleQuery = mock(TypedQuery.class, RETURNS_SELF);
        permissionQuery = mock(TypedQuery.class, RETURNS_SELF);
        when(entities.createQuery(contains("select distinct r.code"), eq(String.class))).thenReturn(roleQuery);
        when(entities.createQuery(contains("select distinct p.code"), eq(String.class))).thenReturn(permissionQuery);
        var employee = mock(AppUser.class);
        when(employee.getActive()).thenReturn(true);
        when(employee.getUsername()).thenReturn("fixture");
        when(users.findById(42L)).thenReturn(Optional.of(employee));

        var category = BeanUtils.instantiateClass(Category.class);
        ReflectionTestUtils.setField(category, "id", 7L);
        category.setName("Hardware");
        category.setActive(true);
        var product = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(product, "id", 1L);
        product.setCategory(category);
        product.setSku("CPU"); product.setName("Processor");
        product.setSellingPrice(BigDecimal.valueOf(100)); product.setMinStockLevel(0L);
        var time = LocalDateTime.parse("2026-11-22T01:00:00");
        product.setCreatedAt(time); product.setUpdatedAt(time);
        when(categories.findForUpdate(7L)).thenReturn(Optional.of(category));
        when(products.findDetail(1L)).thenReturn(Optional.of(product));
        when(products.findForUpdate(1L)).thenReturn(Optional.of(product));
        when(products.search(isNull(), isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(product)));
        when(products.saveAndFlush(product)).thenReturn(product);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void proposal(EmployeeRole role) {
        when(roleQuery.getResultList()).thenReturn(List.of(role.name()));
        when(permissionQuery.getResultList()).thenReturn(ProductPermissionRollout.baselineGrantProposals().stream()
                .filter(grant -> grant.role() == role).map(grant -> grant.permission().code()).toList());
    }

    private static List<MockHttpServletRequestBuilder> writes() {
        return List.of(post(BASE).contentType(MediaType.APPLICATION_JSON).content(CREATE),
                put(BASE + "/1").contentType(MediaType.APPLICATION_JSON).content(INFO),
                put(BASE + "/1/price").contentType(MediaType.APPLICATION_JSON).content(PRICE),
                post(BASE + "/1/deactivate"));
    }

    private static List<MockHttpServletRequestBuilder> operations() {
        var operations = new java.util.ArrayList<>(List.of(get(BASE), get(BASE + "/1")));
        operations.addAll(writes());
        return operations;
    }

    @Test void anonymousGets401EvenWithValidCsrf() throws Exception {
        for (var operation : operations()) mvc.perform(operation.with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(users, entities, products, categories);
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
    }

    @ParameterizedTest @EnumSource(value = EmployeeRole.class, names = {"SALES_STAFF", "WAREHOUSE_STAFF"})
    void baselineStaffProposalsCanReadButNeverMutate(EmployeeRole role) throws Exception {
        proposal(role);
        for (var read : List.of(get(BASE), get(BASE + "/1"))) {
            mvc.perform(read.with(user(PRINCIPAL))).andExpect(status().isOk());
        }
        for (var write : writes()) mvc.perform(write.with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(products, never()).saveAndFlush(any());
        verifyNoInteractions(categories);
    }

    @Test void adminAndMissingGrantsAreDeniedBeforeProductPersistence() throws Exception {
        for (var role : List.of(EmployeeRole.ADMIN, EmployeeRole.MANAGER)) {
            when(roleQuery.getResultList()).thenReturn(List.of(role.name()));
            when(permissionQuery.getResultList()).thenReturn(List.of());
            for (var operation : operations()) mvc.perform(operation.with(user(PRINCIPAL)).with(csrf()))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        verifyNoInteractions(products, categories);
    }

    @Test void managerProposalReachesAllOperationsButCreateDoesNotWriteWithoutInventory() throws Exception {
        proposal(EmployeeRole.MANAGER);
        mvc.perform(get(BASE).with(user(PRINCIPAL))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("1"));
        mvc.perform(get(BASE + "/1").with(user(PRINCIPAL))).andExpect(status().isOk());
        mvc.perform(writes().get(0).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
        verify(products, never()).saveAndFlush(any());
        mvc.perform(writes().get(1).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sellingPrice").value("100"));
        mvc.perform(writes().get(2).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sellingPrice").value("200"));
        mvc.perform(writes().get(3).with(user(PRINCIPAL)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        verify(products, times(3)).saveAndFlush(any());
    }

    @Test void informationGrantCannotSetPriceAndCreateAloneCannotSetInitialPrice() throws Exception {
        when(roleQuery.getResultList()).thenReturn(List.of("MANAGER"));
        when(permissionQuery.getResultList()).thenReturn(List.of("PRODUCT_UPDATE", "PRODUCT_CREATE"));
        mvc.perform(writes().get(2).with(user(PRINCIPAL)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(writes().get(0).with(user(PRINCIPAL)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/1").contentType(MediaType.APPLICATION_JSON)
                        .content(INFO.strip().replace("}", ",\"sellingPrice\":\"200\"}"))
                        .with(user(PRINCIPAL)).with(csrf())).andExpect(status().isBadRequest());
        verifyNoInteractions(products, categories);
    }

    @Test void missingAndInvalidCsrfDenyEveryMutationBeforeActorOrPersistence() throws Exception {
        proposal(EmployeeRole.MANAGER);
        for (var write : writes()) mvc.perform(write.with(user(PRINCIPAL))).andExpect(status().isForbidden());
        for (var write : writes()) mvc.perform(write.with(user(PRINCIPAL)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(users, entities, products, categories);
    }

    @Test void candidateDoesNotOpenUnlistedRoutes() throws Exception {
        proposal(EmployeeRole.MANAGER);
        for (var request : List.of(delete(BASE + "/1"), patch(BASE + "/1"), post(BASE + "/1/reactivate"),
                get(BASE + "/1/price"), get("/api/v1/inventory"), get("/api/v1/suppliers"))) {
            mvc.perform(request.with(user(PRINCIPAL)).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(users, entities, products, categories);
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, EmployeeUserDetailsService.class,
            ProductController.class, ProductService.class, GlobalExceptionHandler.class,
            CurrentActorService.class, RoleResolutionService.class, AuthorizationService.class, PermissionPolicy.class})
    static class CandidateFixture extends SecurityConfig {
        @Bean @Override SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                ApiAuthenticationEntryPoint entryPoint, ApiAccessDeniedHandler deniedHandler,
                SecurityContextRepository contexts, CsrfTokenRepository csrfTokens,
                XorCsrfTokenRequestAttributeHandler csrfHandler) throws Exception {
            return buildApiSecurityFilterChain(http, entryPoint, deniedHandler, contexts, csrfTokens, csrfHandler, true);
        }
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
        @Bean EntityManager entities() { return mock(EntityManager.class); }
        @Bean ProductRepository products() { return mock(ProductRepository.class); }
        @Bean CategoryRepository categories() { return mock(CategoryRepository.class); }
        @Bean UtcTimeProvider time() { return new UtcTimeProvider(Clock.systemUTC()); }
    }
}
