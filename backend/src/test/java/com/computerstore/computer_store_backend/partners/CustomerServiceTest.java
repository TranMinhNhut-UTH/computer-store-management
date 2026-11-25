package com.computerstore.computer_store_backend.partners;

import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.partners.dto.CustomerWriteRequest;
import com.computerstore.computer_store_backend.partners.entity.Customer;
import com.computerstore.computer_store_backend.partners.repository.CustomerRepository;
import com.computerstore.computer_store_backend.partners.service.CustomerService;
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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CustomerServiceTest {
    static final long ID = 9007199254740993L;
    static final LocalDateTime CREATED = LocalDateTime.parse("2026-11-21T08:00:00");
    static final LocalDateTime NOW = CREATED.plusDays(1);
    static final CustomerWriteRequest INPUT = new CustomerWriteRequest(" ac-01 ", " Việt Nam ");
    private final CustomerRepository repo = mock(CustomerRepository.class);
    private final PermissionPolicy policy = mock(PermissionPolicy.class);
    private final UtcTimeProvider clock = mock(UtcTimeProvider.class);
    private final CustomerService service = new CustomerService(repo, policy, clock);

    static Customer customer() {
        var customer = BeanUtils.instantiateClass(Customer.class);
        ReflectionTestUtils.setField(customer, "id", ID);
        customer.setFullName("Distributor");
        customer.setCode("AC-01");
        customer.setCreatedAt(CREATED);
        customer.setUpdatedAt(CREATED);
        return customer;
    }

    @BeforeEach void setup() {
        when(clock.now()).thenReturn(NOW);
        when(repo.saveAndFlush(any(Customer.class))).thenAnswer(call -> {
            Customer customer = call.getArgument(0);
            if (customer.getId() == null) ReflectionTestUtils.setField(customer, "id", ID);
            return customer;
        });
    }

    @Test void listEscapesWildcardsAndUsesStablePagination() {
        var page = PageRequest.of(0, 20, Sort.by("fullName", "id"));
        when(repo.search("%x!%!_!!%", false, page)).thenReturn(new PageImpl<>(List.of(customer())));
        when(policy.availableActions(CUSTOMER_READ, CUSTOMER_CREATE, CUSTOMER_UPDATE))
                .thenReturn(List.of("CUSTOMER_UPDATE"));
        var result = service.list(" x%_! ", false, 0, 20);
        assertEquals(List.of("CUSTOMER_UPDATE"), result.allowedActions());
        assertEquals(Long.toString(ID), result.items().getFirst().id());
        assertEquals(1, result.totalElements());
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.list(null, null, Integer.MAX_VALUE, 100));
    }

    @Test void createNormalizesCodeAndSetsAuditFields() {
        var result = service.create(INPUT);
        assertEquals("AC-01", result.code());
        assertEquals("Việt Nam", result.fullName());
        assertTrue(result.active());
        assertEquals(result.createdAt(), result.updatedAt());
        verify(policy).requirePermission(CUSTOMER_CREATE.code());
    }

    @Test void editPreservesInactiveStateCreationDateAndAllContactFields() {
        var existing = customer();
        existing.setActive(false);
        existing.setPhone("existing-phone");
        existing.setEmail("existing-email");
        existing.setAddress("existing-address");
        existing.setNotes("existing-notes");
        when(repo.findForUpdate(ID)).thenReturn(Optional.of(existing));
        when(repo.findByCode("AC-01")).thenReturn(Optional.of(existing));
        assertFalse(service.update(Long.toString(ID), INPUT).active());
        assertEquals(CREATED, existing.getCreatedAt());
        assertEquals(NOW, existing.getUpdatedAt());
        assertEquals("existing-phone", existing.getPhone());
        assertEquals("existing-email", existing.getEmail());
        assertEquals("existing-address", existing.getAddress());
        assertEquals("existing-notes", existing.getNotes());
        verify(policy).requirePermission(CUSTOMER_UPDATE.code());
    }

    @Test void duplicateCodeAndInvalidIdentityAreRejectedBeforeWrite() {
        when(repo.findByCode("AC-01")).thenReturn(Optional.of(customer()));
        assertCode(ErrorCode.BUSINESS_CONFLICT, () -> service.create(INPUT));
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.create(new CustomerWriteRequest("X", " ")));
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.create(new CustomerWriteRequest("bad code", "Name")));
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.create(new CustomerWriteRequest("X", "N".repeat(151))));
        verify(repo, never()).saveAndFlush(any());
    }

    @Test void detailRequiresReadAndValidId() {
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.detail("9223372036854775808"));
        assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> service.detail("7"));
        verify(policy, times(2)).requirePermission(CUSTOMER_READ.code());
    }

    @Test void salesSelectionChecksActiveUnderLockWithoutMutatingTheCustomer() throws Exception {
        when(repo.lockActiveState(ID)).thenReturn(Optional.of(1), Optional.of(0));
        service.requireActiveForSale(ID);
        assertCode(ErrorCode.BUSINESS_CONFLICT, () -> service.requireActiveForSale(ID));
        assertCode(ErrorCode.RESOURCE_NOT_FOUND, () -> service.requireActiveForSale(7));
        assertCode(ErrorCode.VALIDATION_ERROR, () -> service.requireActiveForSale(0));
        verify(repo, never()).saveAndFlush(any());
        var contract = CustomerService.class.getMethod("requireActiveForSale", long.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertEquals(org.springframework.transaction.annotation.Propagation.MANDATORY, contract.propagation());
    }

    private static void assertCode(ErrorCode expected, Runnable call) {
        assertEquals(expected, assertThrows(BusinessException.class, call::run).code());
    }
}
