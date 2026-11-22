package com.computerstore.computer_store_backend.partners.service;

import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.partners.dto.SupplierPageResponse;
import com.computerstore.computer_store_backend.partners.dto.SupplierResponse;
import com.computerstore.computer_store_backend.partners.dto.SupplierWriteRequest;
import com.computerstore.computer_store_backend.partners.entity.Supplier;
import com.computerstore.computer_store_backend.partners.repository.SupplierRepository;
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
import org.springframework.transaction.annotation.Transactional;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;
import static com.computerstore.computer_store_backend.purchasing.entity.PurchaseStatus.DRAFT;

@Service
@Transactional(readOnly = true)
public class SupplierService {
    private final SupplierRepository suppliers;
    private final PermissionPolicy permissions;
    private final UtcTimeProvider time;

    public SupplierService(SupplierRepository suppliers, PermissionPolicy permissions, UtcTimeProvider time) {
        this.suppliers = suppliers;
        this.permissions = permissions;
        this.time = time;
    }

    public SupplierPageResponse list(String q, Boolean active, int page, int size) {
        var actions = permissions.availableActions(SUPPLIER_READ, SUPPLIER_CREATE, SUPPLIER_UPDATE, SUPPLIER_DEACTIVATE);
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) throw invalid();
        var query = text(q, 200);
        var pattern = query == null ? null : "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = suppliers.search(pattern, active, PageRequest.of(page, size, Sort.by("name", "id")));
        return new SupplierPageResponse(result.map(SupplierService::response).getContent(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), actions);
    }

    public SupplierResponse detail(String id) {
        permissions.requirePermission(SUPPLIER_READ.code());
        return response(suppliers.findById(id(id)).orElseThrow(SupplierService::notFound));
    }

    @Transactional
    public SupplierResponse create(SupplierWriteRequest input) {
        permissions.requirePermission(SUPPLIER_CREATE.code());
        var values = validated(input);
        rejectDuplicate(values.code(), null);
        var supplier = BeanUtils.instantiateClass(Supplier.class);
        applyInformation(supplier, values);
        supplier.setActive(true);
        var now = time.now();
        supplier.setCreatedAt(now);
        supplier.setUpdatedAt(now);
        return response(save(supplier));
    }

    @Transactional
    public SupplierResponse update(String id, SupplierWriteRequest input) {
        permissions.requirePermission(SUPPLIER_UPDATE.code());
        var values = validated(input);
        var supplier = locked(id);
        rejectDuplicate(values.code(), supplier.getId());
        applyInformation(supplier, values);
        supplier.setUpdatedAt(time.now());
        return response(save(supplier));
    }

    @Transactional
    public SupplierResponse deactivate(String id) {
        permissions.requirePermission(SUPPLIER_DEACTIVATE.code());
        var supplier = locked(id);
        if (Boolean.FALSE.equals(supplier.getActive())) return response(supplier);
        if (!suppliers.findPurchases(supplier.getId(), DRAFT, PageRequest.of(0, 1)).isEmpty()) {
            throw conflict("Resolve open purchase drafts before deactivating this supplier.");
        }
        supplier.setActive(false);
        supplier.setUpdatedAt(time.now());
        return response(save(supplier));
    }

    private Supplier locked(String id) {
        return suppliers.findForUpdate(id(id)).orElseThrow(SupplierService::notFound);
    }

    private void rejectDuplicate(String code, Long id) {
        if (suppliers.findByCode(code).filter(row -> id == null || !Objects.equals(row.getId(), id)).isPresent()) throw duplicate();
    }

    private Supplier save(Supplier supplier) {
        try {
            return suppliers.saveAndFlush(supplier);
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    var constraint = violation.getConstraintName();
                    if ("uq_supplier_code".equals(constraint)
                            || (constraint != null && constraint.endsWith(".uq_supplier_code"))) throw duplicate();
                }
            }
            throw failure;
        }
    }

    private static SupplierWriteRequest validated(SupplierWriteRequest input) {
        if (input == null) throw invalid();
        var code = IdentifierNormalizer.businessCode(input.code());
        var name = text(input.name(), 200);
        if (code == null || code.isEmpty() || code.length() > 50 || name == null) throw invalid();
        var phone = text(input.phone(), 30);
        if (phone != null && (!phone.matches("[+0-9(). -]+") || phone.chars().noneMatch(Character::isDigit))) throw invalid();
        var email = text(input.email(), 254);
        if (email != null && !email.matches("[^\\s@]+@[^\\s@.]+(?:\\.[^\\s@.]+)+")) throw invalid();
        var notes = text(input.notes(), 65_535);
        if (notes != null && notes.getBytes(StandardCharsets.UTF_8).length > 65_535) throw invalid();
        return new SupplierWriteRequest(code, name, phone, email, text(input.address(), 500), notes);
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

    private static void applyInformation(Supplier supplier, SupplierWriteRequest values) {
        supplier.setCode(values.code());
        supplier.setName(values.name());
        supplier.setPhone(values.phone());
        supplier.setEmail(values.email());
        supplier.setAddress(values.address());
        supplier.setNotes(values.notes());
    }

    private static SupplierResponse response(Supplier supplier) {
        return new SupplierResponse(supplier.getId().toString(), supplier.getCode(), supplier.getName(),
                supplier.getPhone(), supplier.getEmail(), supplier.getAddress(), supplier.getNotes(),
                Boolean.TRUE.equals(supplier.getActive()), supplier.getCreatedAt().toInstant(ZoneOffset.UTC),
                supplier.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private static BusinessException invalid() { return new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid supplier request."); }
    private static BusinessException notFound() { return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "The supplier was not found."); }
    private static BusinessException conflict(String message) { return new BusinessException(ErrorCode.BUSINESS_CONFLICT, message); }
    private static BusinessException duplicate() { return conflict("A supplier with this code already exists."); }
}
