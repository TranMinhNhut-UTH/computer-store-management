package com.computerstore.computer_store_backend.shared.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone MVC fixture: no Spring Boot context, datasource or Flyway auto-configuration. */
class GlobalExceptionHandlerTest {
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new Fixture())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void validationIncludesStableNestedPathsAndSafeMessages() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"lines\":[{\"sku\":\"\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.instance").value("/fixture"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("lines[0].sku"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Invalid value."))
                .andExpect(content().string(not(containsString("LEAK"))));
    }
    @Test void malformedJsonIsSanitized() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON).content("{secret:LEAK"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors").isEmpty())
                .andExpect(content().string(not(containsString("LEAK"))));
    }
    @Test void validRequestReachesController() throws Exception {
        mvc.perform(post("/fixture").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Valid\",\"lines\":[{\"sku\":\"CPU-1\"}]}"))
                .andExpect(status().isOk());
    }
    @Test void typeMismatchIsBadRequest() throws Exception {
        mvc.perform(get("/fixture/number").param("value", "LEAK"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(content().string(not(containsString("LEAK"))));
    }
    @Test void methodParameterValidationIsBadRequest() throws Exception {
        mvc.perform(get("/fixture/number").param("value", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("value"));
    }
    @Test void notFoundUsesPublicContract() throws Exception { business("missing", 404, "RESOURCE_NOT_FOUND"); }
    @Test void conflictUsesPublicContract() throws Exception { business("conflict", 409, "BUSINESS_CONFLICT"); }
    private void business(String path, int statusCode, String code) throws Exception {
        mvc.perform(get("/fixture/" + path)).andExpect(status().is(statusCode))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.detail").value("Public-safe detail."));
    }
    @Test void unexpectedErrorNeverExposesExceptionOrQuery() throws Exception {
        mvc.perform(get("/fixture/failure").queryParam("password", "LEAK"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.instance").value("/fixture/failure"))
                .andExpect(content().string(not(containsString("LEAK"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @RestController
    static class Fixture {
        @PostMapping("/fixture") void create(@Valid @RequestBody Input input) { }
        @GetMapping("/fixture/number") void number(@RequestParam("value") @Min(1) int value) { }
        @GetMapping("/fixture/missing") void missing() {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "Public-safe detail.");
        }
        @GetMapping("/fixture/conflict") void conflict() {
            throw new BusinessException(ErrorCode.BUSINESS_CONFLICT, "Public-safe detail.");
        }
        @GetMapping("/fixture/failure") void failure() {
            throw new IllegalStateException("SQL password LEAK");
        }
    }
    record Input(@NotBlank(message = "LEAK ${validatedValue}") String name, List<@Valid Line> lines) { }
    record Line(@NotBlank String sku) { }
}
