package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.controller.CategoryController;
import com.computerstore.computer_store_backend.catalog.service.CategoryService;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.*;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual production SecurityConfig, MVC-only context: controller exists but no route is opened. */
@SpringJUnitConfig(CategorySecurityTest.Fixture.class)
@WebAppConfiguration
class CategorySecurityTest {
    @Autowired WebApplicationContext context;
    @Autowired CategoryService service;
    private MockMvc mvc;

    @BeforeEach void setup() {
        reset(service);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test void anonymousCategoryReadsRemain401() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        verifyNoInteractions(service);
        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
    }

    @Test void evenAdminWithCategoryAuthoritiesAndCsrfCannotReachAnyCategoryOperation() throws Exception {
        var subject = user("fixture").authorities(List.of("ROLE_ADMIN", "ROLE_MANAGER", "CATEGORY_READ",
                "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE").stream()
                .map(SimpleGrantedAuthority::new).toList());
        for (String path : List.of("/api/v1/categories","/api/v1/categories/7")) {
            mvc.perform(get(path).with(subject))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        mvc.perform(post("/api/v1/categories").with(subject).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/categories/7").with(subject).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/categories/7/deactivate").with(subject).with(csrf()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void csrfProtectionStillRejectsMutationBeforeController() throws Exception {
        mvc.perform(post("/api/v1/categories/7/deactivate").with(user("fixture").roles("MANAGER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verifyNoInteractions(service);
    }

    @Configuration(proxyBeanMethods=false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,ApiAuthenticationEntryPoint.class,ApiAccessDeniedHandler.class,
            EmployeeUserDetailsService.class,CategoryController.class,GlobalExceptionHandler.class})
    static class Fixture {
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
        @Bean CategoryService categories() { return mock(CategoryService.class); }
    }
}
