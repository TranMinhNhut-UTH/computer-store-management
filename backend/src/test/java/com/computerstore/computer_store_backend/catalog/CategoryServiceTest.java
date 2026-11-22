package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.dto.request.CategoryWriteRequest;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.service.CategoryService;
import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CategoryServiceTest {
    private final CategoryRepository repository = mock(CategoryRepository.class);
    private final RoleResolutionService resolver = mock(RoleResolutionService.class);
    private final Instant now = Instant.parse("2026-11-21T06:00:00.123456789Z");
    private final CategoryService service = new CategoryService(repository,
            new PermissionPolicy(new AuthorizationService(resolver)),
            new UtcTimeProvider(Clock.fixed(now, ZoneOffset.UTC)));
    private final CategoryWriteRequest valid = new CategoryWriteRequest(" cpu ", " Processors ", " Components ");

    @BeforeEach void setup() {
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(42L, "employee"),
                Set.of(EmployeeRole.MANAGER), Set.of("CATEGORY_READ", "CATEGORY_CREATE", "CATEGORY_UPDATE", "CATEGORY_DEACTIVATE")));
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(CategoryTestData.category(7, true)));
        when(repository.findProductReferencesForDeactivation(eq(7L), any())).thenReturn(List.of());
        when(repository.saveAndFlush(any(Category.class))).thenAnswer(call -> {
            Category value = call.getArgument(0);
            if (value.getId() == null) ReflectionTestUtils.setField(value, "id", 7L);
            return value;
        });
    }

    @AfterEach void noHardDeleteWasEverInvoked() {
        assertTrue(mockingDetails(repository).getInvocations().stream()
                .noneMatch(call -> call.getMethod().getName().startsWith("delete")));
    }

    @Test void listReturnsBoundedStablePageAndDtoSnapshot() {
        when(repository.search(isNull(), isNull(), any())).thenReturn(new PageImpl<>(
                List.of(CategoryTestData.category(7, true)), PageRequest.of(0, 20), 1));
        var result = service.list(null, null, 0, 20);
        assertEquals("7", result.items().getFirst().id()); assertEquals(1, result.totalElements());
        var page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).search(isNull(), isNull(), page.capture());
        assertEquals(20, page.getValue().getPageSize());
        assertEquals(Sort.by("name", "id"), page.getValue().getSort());
        assertThrows(UnsupportedOperationException.class, () -> result.items().clear());
    }

    @Test void searchTreatsLikeWildcardsAsLiteralAndKeepsActiveFilter() {
        when(repository.search(anyString(), eq(false), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(1, 10), 0));
        service.list("  cpu%_!  ", false, 1, 10);
        verify(repository).search(eq("%cpu!%!_!!%"), eq(false), any());
    }

    @Test void detailReturnsOnlyTheRequestedCategoryAndUsesDecimalStringId() {
        var category = CategoryTestData.category(9007199254740993L, false);
        when(repository.findById(9007199254740993L)).thenReturn(Optional.of(category));
        var result = service.detail("9007199254740993");
        assertEquals("9007199254740993", result.id()); assertFalse(result.active());
        assertEquals(CategoryTestData.CREATED.toInstant(ZoneOffset.UTC), result.createdAt());
    }

    @Test void missingDetailAndWriteTargetsReturn404() {
        when(repository.findForUpdate(7L)).thenReturn(Optional.empty());
        error(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail("8"));
        error(ErrorCode.RESOURCE_NOT_FOUND, () -> service.update("7", valid));
        error(ErrorCode.RESOURCE_NOT_FOUND, () -> service.deactivate("7"));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void createNormalizesBeforeUniquenessAndUsesServerUtcAuditAndActiveFlag() {
        var result = service.create(valid);
        assertEquals("CPU", result.code()); assertEquals("Processors", result.name());
        assertEquals("Components", result.description()); assertTrue(result.active());
        assertEquals(Instant.parse("2026-11-21T06:00:00.123456Z"), result.createdAt());
        assertEquals(result.createdAt(), result.updatedAt());
        verify(repository).findByCode("CPU");
    }

    @Test void duplicateCreateIs409BeforeWriting() {
        when(repository.findByCode("CPU")).thenReturn(Optional.of(CategoryTestData.category(9, false)));
        error(ErrorCode.BUSINESS_CONFLICT, () -> service.create(valid));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void updateCanKeepItsOwnCodeAndCannotReactivateOrRewriteCreatedAt() {
        var old = CategoryTestData.category(7, false);
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(old));
        when(repository.findByCode("CPU")).thenReturn(Optional.of(old));
        var result = service.update("7", new CategoryWriteRequest("cpu", "New label", null));
        assertFalse(result.active()); assertNull(result.description()); assertEquals("New label", result.name());
        assertEquals(CategoryTestData.CREATED.toInstant(ZoneOffset.UTC), result.createdAt());
        assertEquals(Instant.parse("2026-11-21T06:00:00.123456Z"), result.updatedAt());
    }

    @Test void updateRejectsAnotherCategorysCodeWithoutMutatingTheTarget() {
        var old = CategoryTestData.category(7, true);
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(old));
        when(repository.findByCode("RAM")).thenReturn(Optional.of(CategoryTestData.category(8, true)));
        error(ErrorCode.BUSINESS_CONFLICT, () -> service.update("7", new CategoryWriteRequest("ram", "Memory", null)));
        assertEquals("CPU", old.getCode()); verify(repository, never()).saveAndFlush(any());
    }

    @Test void deactivateUnreferencedCategoryOnlyChangesItsStateAndUpdatedAt() {
        var result = service.deactivate("7");
        assertFalse(result.active()); assertEquals("CPU", result.code());
        assertEquals(CategoryTestData.CREATED.toInstant(ZoneOffset.UTC), result.createdAt());
        var limit = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findProductReferencesForDeactivation(eq(7L), limit.capture());
        assertEquals(1, limit.getValue().getPageSize());
    }

    @Test void anyProductReferenceDeniesDeactivationIncludingInactiveProductsWithoutChangingProduct() {
        var product = mock(Product.class);
        when(repository.findProductReferencesForDeactivation(eq(7L), any())).thenReturn(List.of(product));
        for (boolean active : List.of(true, false)) {
            when(product.getActive()).thenReturn(active);
            error(ErrorCode.BUSINESS_CONFLICT, () -> service.deactivate("7"));
        }
        verify(repository, never()).saveAndFlush(any());
        assertTrue(mockingDetails(product).getInvocations().isEmpty());
    }

    @Test void alreadyInactiveDeactivationIsAnAuthorizedNoOp() {
        when(repository.findForUpdate(7L)).thenReturn(Optional.of(CategoryTestData.category(7, false)));
        assertFalse(service.deactivate("7").active());
        verify(repository, never()).findProductReferencesForDeactivation(anyLong(), any());
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void nullEmptyInvalidAndOversizedFieldsAre400BeforePersistence() {
        for (var request : List.of(new CategoryWriteRequest(null, "Name", null),
                new CategoryWriteRequest(" ", "Name", null), new CategoryWriteRequest("bad code", "Name", null),
                new CategoryWriteRequest("X".repeat(51), "Name", null), new CategoryWriteRequest("CPU", null, null),
                new CategoryWriteRequest("CPU", " ", null), new CategoryWriteRequest("CPU", "x".repeat(121), null),
                new CategoryWriteRequest("CPU", "Name", "x".repeat(501)), new CategoryWriteRequest("CPU", "\uD800", null))) {
            error(ErrorCode.VALIDATION_ERROR, () -> service.create(request));
        }
        error(ErrorCode.VALIDATION_ERROR, () -> service.create(null));
        verify(repository, never()).saveAndFlush(any());
    }

    @Test void varcharLimitsCountSupplementaryUnicodeCharactersAndEmptyDescriptionStaysDistinctFromNull() {
        var result = service.create(new CategoryWriteRequest("X".repeat(50), "\uD83D\uDE80".repeat(120), "\uD83D\uDE80".repeat(500)));
        assertEquals(120, result.name().codePointCount(0, result.name().length()));
        assertEquals(500, result.description().codePointCount(0, result.description().length()));
        assertEquals("", service.create(new CategoryWriteRequest("RAM", "RAM", " ")).description());
    }

    @Test void invalidIdsPaginationAndSearchAre400() {
        for (String id : new String[]{null, "", "0", "01", "-1", "+1", "1e2", "9223372036854775808"}) {
            error(ErrorCode.VALIDATION_ERROR, () -> service.detail(id));
        }
        for (int[] page : new int[][]{{-1,20},{0,0},{0,101},{Integer.MAX_VALUE,100}}) {
            error(ErrorCode.VALIDATION_ERROR, () -> service.list(null, null, page[0], page[1]));
        }
        error(ErrorCode.VALIDATION_ERROR, () -> service.list("x".repeat(121), null, 0, 20));
    }

    @Test void knownUniqueConstraintRaceIs409ButOtherIntegrityFailuresAreNotMisclassified() {
        for (String constraint : List.of("uq_category_code", "category.uq_category_code")) {
            doThrow(new DataIntegrityViolationException("SQL_SENTINEL",
                    new ConstraintViolationException("SQL_SENTINEL", new SQLException("SQL_SENTINEL"), constraint)))
                    .when(repository).saveAndFlush(any());
            var failure = error(ErrorCode.BUSINESS_CONFLICT, () -> service.create(valid));
            assertFalse(failure.publicDetail().contains("SQL_SENTINEL")); assertNull(failure.getCause());
        }
        var other = new DataIntegrityViolationException("INTERNAL_SENTINEL");
        doThrow(other).when(repository).saveAndFlush(any());
        assertSame(other, assertThrows(DataIntegrityViolationException.class, () -> service.create(valid)));
    }

    @Test void anonymousIs401AndAdminWithoutExplicitMappingIs403BeforeAnyCategoryAccess() {
        when(resolver.resolveCurrent()).thenThrow(new BusinessException(ErrorCode.UNAUTHENTICATED, "Authentication is required."));
        for (var action : actions()) error(ErrorCode.UNAUTHENTICATED, action);
        doReturn(new ResolvedAccess(new CurrentActor(42L,"employee"), Set.of(EmployeeRole.ADMIN), Set.of()))
                .when(resolver).resolveCurrent();
        for (var action : actions()) error(ErrorCode.ACCESS_DENIED, action);
        verifyNoInteractions(repository);
    }

    @Test void readGrantDoesNotAuthorizeAnyWriteAndWriteGrantDoesNotImplyRead() {
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(42L,"employee"), Set.of(EmployeeRole.MANAGER), Set.of("CATEGORY_READ")));
        error(ErrorCode.ACCESS_DENIED, () -> service.create(valid));
        error(ErrorCode.ACCESS_DENIED, () -> service.update("7",valid));
        error(ErrorCode.ACCESS_DENIED, () -> service.deactivate("7"));
        when(resolver.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(42L,"employee"), Set.of(EmployeeRole.MANAGER), Set.of("CATEGORY_CREATE")));
        error(ErrorCode.ACCESS_DENIED, () -> service.detail("7"));
        verifyNoInteractions(repository);
    }

    private List<Runnable> actions() {
        return List.of(() -> service.list(null,null,0,20), () -> service.detail("7"), () -> service.create(valid),
                () -> service.update("7",valid), () -> service.deactivate("7"));
    }
    private BusinessException error(ErrorCode code, Runnable action) {
        var error = assertThrows(BusinessException.class, action::run);
        assertEquals(code, error.code()); return error;
    }
}
