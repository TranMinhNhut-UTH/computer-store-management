package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.controller.CategoryController;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.service.CategoryService;
import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.shared.error.*;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Standalone contract verification only; real filter denyAll is tested separately. */
class CategoryHttpTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final RoleResolutionService resolver = mock(RoleResolutionService.class);
    private MockMvc mvc;
    private static final String BASE = "/api/v1/categories";
    private static final String BODY = "{\"code\":\" cpu \",\"name\":\" Processors \",\"description\":null}";

    @BeforeEach void setup() {
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(42L,"employee"), Set.of(EmployeeRole.MANAGER),
                Set.of("CATEGORY_READ","CATEGORY_CREATE","CATEGORY_UPDATE","CATEGORY_DEACTIVATE")));
        when(repository.findById(7L)).thenReturn(Optional.of(CategoryTestData.category(7,true)));
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(CategoryTestData.category(7,true)));
        when(repository.findProductReferencesForDeactivation(eq(7L), any())).thenReturn(List.of());
        when(repository.saveAndFlush(any(Category.class))).thenAnswer(call -> {
            Category category = call.getArgument(0);
            if (category.getId() == null) ReflectionTestUtils.setField(category,"id",7L);
            return category;
        });
        var service = new CategoryService(repository, new PermissionPolicy(new AuthorizationService(resolver)),
                new UtcTimeProvider(Clock.systemUTC()));
        mvc = MockMvcBuilders.standaloneSetup(new CategoryController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void listAndDetailUseDtoPageDecimalStringIdAndUtcTimestamps() throws Exception {
        when(repository.search(isNull(), isNull(), any())).thenReturn(new PageImpl<>(
                List.of(CategoryTestData.category(7,true)), PageRequest.of(0,20),1));
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value("7"))
                .andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-20T01:02:03Z"))
                .andExpect(jsonPath("$.content").doesNotExist());
        mvc.perform(get(BASE+"/7")).andExpect(status().isOk()).andExpect(jsonPath("$.id").value("7"))
                .andExpect(jsonPath("$.code").value("CPU")).andExpect(jsonPath("$.products").doesNotExist())
                .andExpect(jsonPath("$.hibernateLazyInitializer").doesNotExist());
    }

    @Test void createUpdateAndDeactivateHaveDistinctCommandsAndStatuses() throws Exception {
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("Location",BASE+"/7"))
                .andExpect(jsonPath("$.code").value("CPU")).andExpect(jsonPath("$.active").value(true));
        mvc.perform(put(BASE+"/7").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
        mvc.perform(post(BASE+"/7/deactivate")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
    }

    @Test void invalidJsonRequiredFieldsAndOversizedDataAreSafe400() throws Exception {
        for (String body : List.of("{", "{}", "{\"code\":\"CPU\",\"name\":\" \"}",
                "{\"code\":\"CPU\",\"name\":\""+"x".repeat(121)+"\"}")) {
            mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR")).andExpect(jsonPath("$.fieldErrors").isArray());
        }
        mvc.perform(get(BASE).param("page","-1")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE).param("size","not-a-number")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE+"/01")).andExpect(status().isBadRequest());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void clientIdActiveActorAndAuditFieldsAreRejectedOnCreateAndUpdate() throws Exception {
        for (String field : List.of("id","active","createdAt","updatedAt","actorId")) {
            String body = "{\"code\":\"CPU\",\"name\":\"Processors\",\""+field+"\":\"CLIENT_SENTINEL\"}";
            mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                    .andExpect(content().string(not(containsString("CLIENT_SENTINEL"))));
            mvc.perform(put(BASE+"/7").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(repository);
    }

    @Test void missingAndDuplicateAndReferencedCategoryUse404And409() throws Exception {
        mvc.perform(get(BASE+"/8")).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        when(repository.findByCode("CPU")).thenReturn(Optional.of(CategoryTestData.category(8,true)));
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
        when(repository.findProductReferencesForDeactivation(eq(7L), any())).thenReturn(List.of(mock(Product.class)));
        mvc.perform(post(BASE+"/7/deactivate")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("BUSINESS_CONFLICT"));
    }

    @Test void serviceAuthorizationKeeps401And403ProblemDetailAndNoAdminWildcard() throws Exception {
        when(resolver.resolveCurrent()).thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED,"Authentication is required."));
        mvc.perform(get(BASE+"/7")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        doReturn(new ResolvedAccess(new CurrentActor(42L,"employee"),Set.of(EmployeeRole.ADMIN),Set.of()))
                .when(resolver).resolveCurrent();
        mvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.detail").value("Access is denied."));
        verifyNoInteractions(repository);
    }

    @Test void infrastructureAndUnknownIntegrityErrorsAreSanitized500() throws Exception {
        when(repository.findById(7L)).thenThrow(new DataIntegrityViolationException("SQL_INTERNAL_SENTINEL"));
        mvc.perform(get(BASE+"/7").param("secret","QUERY_SENTINEL"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("SQL_INTERNAL_SENTINEL"))))
                .andExpect(content().string(not(containsString("QUERY_SENTINEL"))))
                .andExpect(jsonPath("$.instance").value(BASE+"/7"));
    }

    @Test void noHardDeleteReactivateOrPatchOperationIsImplemented() throws Exception {
        mvc.perform(delete(BASE+"/7")).andExpect(status().isMethodNotAllowed());
        mvc.perform(patch(BASE+"/7").contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isMethodNotAllowed());
        mvc.perform(post(BASE+"/7/reactivate")).andExpect(status().isNotFound());
        verifyNoInteractions(repository);
    }
}
