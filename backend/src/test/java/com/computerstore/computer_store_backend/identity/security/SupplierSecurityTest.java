package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.partners.controller.SupplierController;
import com.computerstore.computer_store_backend.partners.entity.Supplier;
import com.computerstore.computer_store_backend.partners.repository.SupplierRepository;
import com.computerstore.computer_store_backend.partners.service.SupplierService;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

@SpringJUnitConfig(SupplierSecurityTest.Fixture.class)
@WebAppConfiguration
class SupplierSecurityTest {
    private static final String BASE = "/api/v1/suppliers";
    private static final String BODY = "{\"code\":\" ac-01 \",\"name\":\"Supplier\"}";
    private static final List<String> MANAGER_GRANTS = List.of(
            "SUPPLIER_READ", "SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_DEACTIVATE");

    @Autowired WebApplicationContext context;
    @Autowired SupplierRepository suppliers;
    @Autowired AppUserRepository users;
    @Autowired EntityManager entities;
    private TypedQuery<String> roles, grants;
    private EmployeePrincipal principal;
    private MockMvc mvc;

    @BeforeEach @SuppressWarnings("unchecked")
    void setup() {
        reset(suppliers, users, entities);
        roles = mock(TypedQuery.class, RETURNS_SELF);
        grants = mock(TypedQuery.class, RETURNS_SELF);
        when(entities.createQuery(contains("select distinct r.code"), eq(String.class))).thenReturn(roles);
        when(entities.createQuery(contains("select distinct p.code"), eq(String.class))).thenReturn(grants);
        var employee = mock(AppUser.class);
        when(employee.getId()).thenReturn(42L);
        when(employee.getUsername()).thenReturn("employee");
        when(employee.getPasswordHash()).thenReturn("test-only");
        when(employee.getActive()).thenReturn(true);
        when(users.findByUsername("employee")).thenReturn(Optional.of(employee));
        when(users.findById(42L)).thenReturn(Optional.of(employee));
        principal = new EmployeeUserDetailsService(users).loadUserByUsername("employee");
        principal.eraseCredentials();
        clearInvocations(users);
        when(suppliers.search(isNull(), isNull(), any())).thenReturn(new PageImpl<>(List.of(supplier())));
        when(suppliers.findById(7L)).thenReturn(Optional.of(supplier()));
        when(suppliers.findForUpdate(7L)).thenReturn(Optional.of(supplier()));
        when(suppliers.saveAndFlush(any(Supplier.class))).thenAnswer(call -> {
            Supplier row = call.getArgument(0);
            if (row.getId() == null) ReflectionTestUtils.setField(row, "id", 7L);
            return row;
        });
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private Supplier supplier() {
        var row = BeanUtils.instantiateClass(Supplier.class);
        ReflectionTestUtils.setField(row, "id", 7L);
        row.setCode("AC-01");
        row.setName("Supplier");
        row.setCreatedAt(LocalDateTime.parse("2026-11-21T12:00:00"));
        row.setUpdatedAt(row.getCreatedAt());
        return row;
    }

    private List<MockHttpServletRequestBuilder> mutations() {
        return List.of(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY),
                put(BASE + "/7").contentType(MediaType.APPLICATION_JSON).content(BODY),
                post(BASE + "/7/deactivate"));
    }

    @Test void anonymousRequestsReturn401() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get(BASE + "/7")).andExpect(status().isUnauthorized());
        for (var request : mutations()) mvc.perform(request.with(csrf())).andExpect(status().isUnauthorized());
        verifyNoInteractions(suppliers, users, entities);
        assertTrue(context.getBeansOfType(DataSource.class).isEmpty());
    }

    @Test void managerCanUseAllFiveRoutesAndReceivesOnlyGrantedActions() throws Exception {
        when(roles.getResultList()).thenReturn(List.of("MANAGER"));
        when(grants.getResultList()).thenReturn(MANAGER_GRANTS);
        mvc.perform(get(BASE).with(user(principal))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value("7"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.contains(
                        "SUPPLIER_CREATE", "SUPPLIER_UPDATE", "SUPPLIER_DEACTIVATE")));
        mvc.perform(get(BASE + "/7").with(user(principal))).andExpect(status().isOk());
        mvc.perform(mutations().get(0).with(user(principal)).with(csrf())).andExpect(status().isCreated())
                .andExpect(header().string("Location", BASE + "/7"));
        mvc.perform(mutations().get(1).with(user(principal)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        mvc.perform(mutations().get(2).with(user(principal)).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test void warehouseReadIsAllowedAndWritesAreDenied() throws Exception {
        when(roles.getResultList()).thenReturn(List.of("WAREHOUSE_STAFF"));
        when(grants.getResultList()).thenReturn(List.of("SUPPLIER_READ"));
        mvc.perform(get(BASE).with(user(principal))).andExpect(status().isOk()).andExpect(jsonPath("$.allowedActions").isEmpty());
        for (var request : mutations()) mvc.perform(request.with(user(principal)).with(csrf())).andExpect(status().isForbidden());
        verify(suppliers, never()).saveAndFlush(any());
    }

    @Test void adminAndSalesWithoutGrantsAreDenied() throws Exception {
        for (var role : List.of("ADMIN", "SALES_STAFF")) {
            when(roles.getResultList()).thenReturn(List.of(role));
            when(grants.getResultList()).thenReturn(List.of());
            mvc.perform(get(BASE).with(user(principal))).andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
            for (var request : mutations()) mvc.perform(request.with(user(principal)).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(suppliers);
    }

    @Test void csrfAndUnlistedRoutesRemainDeniedAndFieldsAreValidated() throws Exception {
        when(roles.getResultList()).thenReturn(List.of("MANAGER"));
        when(grants.getResultList()).thenReturn(MANAGER_GRANTS);
        for (var request : mutations()) mvc.perform(request.with(user(principal))).andExpect(status().isForbidden());
        for (var request : mutations()) mvc.perform(request.with(user(principal)).with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        for (var request : List.of(delete(BASE + "/7"), post(BASE + "/7/reactivate"), get("/api/v1/purchases"))) {
            mvc.perform(request.with(user(principal)).with(csrf())).andExpect(status().isForbidden());
        }
        mvc.perform(post(BASE).with(user(principal)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"X\",\"active\":true}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/999").with(user(principal))).andExpect(status().isNotFound());
        verify(suppliers, never()).saveAndFlush(any());
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, EmployeeUserDetailsService.class,
            SupplierController.class, SupplierService.class, GlobalExceptionHandler.class, CurrentActorService.class,
            RoleResolutionService.class, AuthorizationService.class, PermissionPolicy.class})
    static class Fixture extends SecurityConfig {
        @Bean @Override SecurityFilterChain apiSecurityFilterChain(HttpSecurity http,
                ApiAuthenticationEntryPoint entry, ApiAccessDeniedHandler denied, SecurityContextRepository contexts,
                CsrfTokenRepository csrf, XorCsrfTokenRequestAttributeHandler handler) throws Exception {
            return buildSupplierBoundary(http, entry, denied, contexts, csrf, handler, true);
        }
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
        @Bean SupplierRepository suppliers() { return mock(SupplierRepository.class); }
        @Bean EntityManager entities() { return mock(EntityManager.class); }
        @Bean UtcTimeProvider time() { return new UtcTimeProvider(Clock.systemUTC()); }
    }
}
