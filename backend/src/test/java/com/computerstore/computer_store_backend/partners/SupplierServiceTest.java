package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.partners.dto.SupplierWriteRequest;
import com.computerstore.computer_store_backend.partners.entity.Supplier;
import com.computerstore.computer_store_backend.partners.repository.SupplierRepository;
import com.computerstore.computer_store_backend.partners.service.SupplierService;
import com.computerstore.computer_store_backend.purchasing.entity.Purchase;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;
import static com.computerstore.computer_store_backend.purchasing.entity.PurchaseStatus.DRAFT;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SupplierServiceTest {
    static final long ID = 9007199254740993L;
    static final LocalDateTime CREATED = LocalDateTime.parse("2026-11-21T08:00:00");
    static final LocalDateTime NOW = CREATED.plusDays(1);
    static final SupplierWriteRequest INPUT = new SupplierWriteRequest(" ac-01 ", " Việt Nam ",
            " +84 123 ", " Contact@Example.vn ", " ", null);
    private final SupplierRepository repo = mock(SupplierRepository.class);
    private final PermissionPolicy policy = mock(PermissionPolicy.class);
    private final UtcTimeProvider clock = mock(UtcTimeProvider.class);
    private final SupplierService service = new SupplierService(repo, policy, clock);

    static Supplier supplier() {
        var supplier = BeanUtils.instantiateClass(Supplier.class);
        ReflectionTestUtils.setField(supplier, "id", ID);
        supplier.setName("Distributor");
        supplier.setCode("AC-01");
        supplier.setCreatedAt(CREATED);
        supplier.setUpdatedAt(CREATED);
        return supplier;
    }

    @BeforeEach void setup() {
        when(clock.now()).thenReturn(NOW);
        when(repo.saveAndFlush(any(Supplier.class))).thenAnswer(call -> {
            Supplier supplier = call.getArgument(0);
            if (supplier.getId() == null) ReflectionTestUtils.setField(supplier, "id", ID);
            return supplier;
        });
    }

    @Test void listEscapesWildcardsAndUsesStablePagination() {
        var page = PageRequest.of(0, 20, Sort.by("name", "id"));
        when(repo.search("%x!%!_!!%", false, page)).thenReturn(new PageImpl<>(List.of(supplier())));
        when(policy.availableActions(SUPPLIER_READ, SUPPLIER_CREATE, SUPPLIER_UPDATE, SUPPLIER_DEACTIVATE))
                .thenReturn(List.of("SUPPLIER_UPDATE"));
        var result = service.list(" x%_! ", false, 0, 20);
        assertEquals(List.of("SUPPLIER_UPDATE"), result.allowedActions());
        assertEquals(Long.toString(ID), result.items().getFirst().id());
        assertEquals(1, result.totalElements());
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.list(null, null, Integer.MAX_VALUE, 100));
    }

    @Test void createNormalizesCodeAndContactAndSetsAuditFields() {
        var result = service.create(INPUT);
        assertEquals("AC-01", result.code());
        assertEquals("Việt Nam", result.name());
        assertEquals("+84 123", result.phone());
        assertEquals("Contact@Example.vn", result.email());
        assertNull(result.address());
        assertTrue(result.active());
        assertEquals(result.createdAt(), result.updatedAt());
        verify(policy).requirePermission(SUPPLIER_CREATE.code());
    }

    @Test void editPreservesInactiveStateAndCreationDateAndAcceptsOwnCode() {
        var existing = supplier();
        existing.setActive(false);
        when(repo.findForUpdate(ID)).thenReturn(Optional.of(existing));
        when(repo.findByCode("AC-01")).thenReturn(Optional.of(existing));
        assertFalse(service.update(Long.toString(ID), INPUT).active());
        assertEquals(CREATED, existing.getCreatedAt());
        assertEquals(NOW, existing.getUpdatedAt());
        verify(policy).requirePermission(SUPPLIER_UPDATE.code());
    }

    @Test void duplicateCodeAndInvalidContactAreRejectedBeforeWrite() {
        when(repo.findByCode("AC-01")).thenReturn(Optional.of(supplier()));
        assertCode(ErrorCode.BUSINESS_CONFLICT, () -> service.create(INPUT));
        assertCode(ErrorCode.VALIDATION_ERROR,
                () -> service.create(new SupplierWriteRequest("X", "Name", "letters", null, null, null)));
        assertCode(ErrorCode.VALIDATION_ERROR,
                () -> service.create(new SupplierWriteRequest("X", "Name", null, "a b@example.vn", null, null)));
        assertCode(ErrorCode.VALIDATION_ERROR,
                () -> service.create(new SupplierWriteRequest("X", "Name", null, null, null, "ế".repeat(22000))));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test void openDraftBlocksDeactivationAfterLockAndLeavesSupplierUntouched() {
        var existing = supplier();
        when(repo.findForUpdate(ID)).thenReturn(Optional.of(existing));
        when(repo.findPurchases(ID, DRAFT, PageRequest.of(0, 1))).thenReturn(List.of(mock(Purchase.class)));
        assertCode(ErrorCode.BUSINESS_CONFLICT, () -> service.deactivate(Long.toString(ID)));
        assertTrue(existing.getActive());
        assertEquals(CREATED, existing.getUpdatedAt());
        var order = inOrder(repo);
        order.verify(repo).findForUpdate(ID);
        order.verify(repo).findPurchases(ID, DRAFT, PageRequest.of(0, 1));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test void deactivateWritesOnlySupplierAndRepeatIsNoOp() {
        var existing = supplier();
        when(repo.findForUpdate(ID)).thenReturn(Optional.of(existing));
        assertFalse(service.deactivate(Long.toString(ID)).active());
        assertFalse(service.deactivate(Long.toString(ID)).active());
        verify(repo).findPurchases(ID, DRAFT, PageRequest.of(0, 1));
        verify(repo).saveAndFlush(existing);
        verify(clock).now();
    }

    @Test void detailRequiresReadAndValidId() {
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.detail("9223372036854775808"));
        assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail("7"));
        verify(policy, times(2)).requirePermission(SUPPLIER_READ.code());
    }

    private static void assertCode(ErrorCode expected, Runnable call) {
        assertEquals(expected, assertThrows(BusinessException.class, call::run).code());
    }
}
