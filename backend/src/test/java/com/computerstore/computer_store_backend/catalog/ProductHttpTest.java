package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.controller.ProductController;
import com.computerstore.computer_store_backend.catalog.dto.response.ProductResponse;
import com.computerstore.computer_store_backend.catalog.service.ProductService;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.*;
import com.computerstore.computer_store_backend.shared.error.GlobalExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitConfig(ProductHttpTest.Fixture.class)
@WebAppConfiguration
class ProductHttpTest {
    private static final String BASE = "/api/v1/products";
    private static final String INFO = """
            {"sku":"SKU","name":"Name","categoryId":"7","minStockLevel":"0"}
            """;
    @Autowired WebApplicationContext context;
    @Autowired ProductService service;
    @Autowired ApiAccessDeniedHandler denied;

    private List<MockHttpServletRequestBuilder> writes() {
        return List.of(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"information\":" + INFO + ",\"sellingPrice\":\"100\"}"),
                put(BASE + "/42").contentType(MediaType.APPLICATION_JSON).content(INFO),
                put(BASE + "/42/price").contentType(MediaType.APPLICATION_JSON).content("{\"sellingPrice\":\"100\"}"),
                post(BASE + "/42/deactivate"));
    }

    @Test void realSecurityDeniesEveryUnreleasedProductRouteEvenForAuthenticatedManager() throws Exception {
        reset(service);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        mvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/42")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE).with(user("manager").roles("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/42").with(user("admin").roles("ADMIN"))).andExpect(status().isForbidden());
        for (var command : writes()) {
            mvc.perform(command.with(user("manager").roles("MANAGER")).with(csrf())).andExpect(status().isForbidden());
        }
        verifyNoInteractions(service);
    }

    @Test void csrfRejectsAllMutationsBeforeUseCase() throws Exception {
        reset(service);
        clearInvocations(denied);
        var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        for (var command : writes()) {
            mvc.perform(command.with(user("manager"))).andExpect(status().isForbidden());
        }
        for (var command : writes()) {
            mvc.perform(command.with(user("manager")).with(csrf().useInvalidToken())).andExpect(status().isForbidden());
        }
        verify(denied, times(4)).handle(any(), any(), isA(MissingCsrfTokenException.class));
        verify(denied, times(4)).handle(any(), any(), isA(InvalidCsrfTokenException.class));
        verifyNoInteractions(service);
    }

    @Test void standaloneContractRejectsPriceInOrdinaryUpdateAndImmutableFields() throws Exception {
        reset(service);
        var mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        for (String field : List.of("sellingPrice", "active", "createdAt", "actorId")) {
            var body = INFO.strip().replace("}", ",\"" + field + "\":\"not-supported\"}");
            mvc.perform(put(BASE + "/42").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                .content("{\"information\":{},\"sellingPrice\":\"-1\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(delete(BASE + "/42")).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(BASE + "/42/reactivate")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }

    @Test void standaloneDispatchKeepsPriceSeparateAndSerializesItAsString() throws Exception {
        reset(service);
        var now = Instant.parse("2026-11-22T02:00:00Z");
        when(service.updatePrice(eq("42"), any())).thenReturn(new ProductResponse("42", "SKU", "Name",
                "7", "Category", null, null, null, "9999999999999999999", "0", true, now, now));
        var mvc = MockMvcBuilders.standaloneSetup(new ProductController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(put(BASE + "/42/price").contentType(MediaType.APPLICATION_JSON)
                .content("{\"sellingPrice\":\"9999999999999999999\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sellingPrice").isString())
                .andExpect(jsonPath("$.sellingPrice").value("9999999999999999999"));
        verify(service, never()).update(any(), any());
    }

    @Configuration(proxyBeanMethods = false) @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class,
            EmployeeUserDetailsService.class, ProductController.class, GlobalExceptionHandler.class})
    static class Fixture {
        @Bean AppUserRepository users() { return mock(AppUserRepository.class); }
        @Bean ProductService products() { return mock(ProductService.class); }
        @Bean ApiAccessDeniedHandler denied() { return spy(new ApiAccessDeniedHandler()); }
    }
}
