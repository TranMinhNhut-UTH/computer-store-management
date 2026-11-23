package com.computerstore.computer_store_backend.partners.service;

import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.partners.dto.CustomerPageResponse;
import com.computerstore.computer_store_backend.partners.dto.CustomerResponse;
import com.computerstore.computer_store_backend.partners.dto.CustomerWriteRequest;
import com.computerstore.computer_store_backend.partners.entity.Customer;
import com.computerstore.computer_store_backend.partners.repository.CustomerRepository;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import com.computerstore.computer_store_backend.shared.validation.IdentifierNormalizer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;

@Service
@Transactional(readOnly = true)
public class CustomerService {
    private final CustomerRepository customers;
    private final PermissionPolicy permissions;
    private final UtcTimeProvider time;

    public CustomerService(CustomerRepository customers, PermissionPolicy permissions, UtcTimeProvider time) {
        this.customers = customers;
        this.permissions = permissions;
        this.time = time;
    }

    public CustomerPageResponse list(String q, Boolean active, int page, int size) {
        var actions = permissions.availableActions(CUSTOMER_READ, CUSTOMER_CREATE, CUSTOMER_UPDATE);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw invalid();
        var query = text(q, 200);
        var pattern = query == null ? null : "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = customers.search(pattern, active, PageRequest.of(page, size, Sort.by("fullName", "id")));
        return new CustomerPageResponse(result.map(CustomerService::response).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), actions);
    }

    public CustomerResponse detail(String id) {
        permissions.requirePermission(CUSTOMER_READ.code());
        return response(customers.findById(id(id)).orElseThrow(CustomerService::notFound));
    }

    @Transactional
    public CustomerResponse create(CustomerWriteRequest input) {
        permissions.requirePermission(CUSTOMER_CREATE.code());
        var values = validated(input);
        rejectDuplicate(values.code(), null);
        var customer = BeanUtils.instantiateClass(Customer.class);
        applyInformation(customer, values);
        customer.setActive(true);
        var now = time.now();
        customer.setCreatedAt(now);
        customer.setUpdatedAt(now);
        return response(save(customer));
    }

    @Transactional
    public CustomerResponse update(String id, CustomerWriteRequest input) {
        permissions.requirePermission(CUSTOMER_UPDATE.code());
        var values = validated(input);
        var customer = locked(id);
        rejectDuplicate(values.code(), customer.getId());
        applyInformation(customer, values);
        customer.setUpdatedAt(time.now());
        return response(save(customer));
    }

    /** Called inside a Sales write transaction after Sales authorizes the operation. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveForSale(long customerId) {
        var customer = customers.findForUpdate(customerId).orElseThrow(CustomerService::notFound);
        if (!Boolean.TRUE.equals(customer.getActive())) {
            throw conflict("An inactive customer cannot be selected for a sale.");
        }
    }

    private Customer locked(String id) {
        return customers.findForUpdate(id(id)).orElseThrow(CustomerService::notFound);
    }

    private void rejectDuplicate(String code, Long id) {
        if (customers.findByCode(code).filter(row -> id == null || !Objects.equals(row.getId(), id)).isPresent()) throw duplicate();
    }

    private Customer save(Customer customer) {
        try {
            return customers.saveAndFlush(customer);
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    var constraint = violation.getConstraintName();
                    if ("uq_customer_code".equals(constraint)
                            || (constraint != null && constraint.endsWith(".uq_customer_code"))) throw duplicate();
                }
            }
            throw failure;
        }
    }

    private static CustomerWriteRequest validated(CustomerWriteRequest input) {
        if (input == null) throw invalid();
        var code = IdentifierNormalizer.businessCode(input.code());
        var fullName = text(input.fullName(), 150);
        if (code == null || code.isEmpty() || code.length() > 50 || fullName == null) throw invalid();
        return new CustomerWriteRequest(code, fullName);
    }

    private static String text(String value, int max) {
        if (value == null) return null;
        var trimmed = value.strip();
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(trimmed) || trimmed.codePointCount(0, trimmed.length()) > max) throw invalid();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { throw invalid(); }
    }

    private static void applyInformation(Customer customer, CustomerWriteRequest values) {
        customer.setCode(values.code());
        customer.setFullName(values.fullName());
    }

    private static CustomerResponse response(Customer customer) {
        return new CustomerResponse(customer.getId().toString(), customer.getCode(), customer.getFullName(),
                Boolean.TRUE.equals(customer.getActive()), customer.getCreatedAt().toInstant(ZoneOffset.UTC),
                customer.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid customer request."); }
    private static BusinessException notFound() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "The customer was not found."); }
    private static BusinessException conflict(String message) { return new BusinessException(ErrorCode.BUSINESS_CONFLICT, message); }
    private static BusinessException duplicate() { return conflict("A customer with this code already exists."); }
}
