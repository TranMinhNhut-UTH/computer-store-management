package com.computerstore.computer_store_backend.catalog.service;

import com.computerstore.computer_store_backend.catalog.dto.request.CategoryWriteRequest;
import com.computerstore.computer_store_backend.catalog.dto.response.CategoryPageResponse;
import com.computerstore.computer_store_backend.catalog.dto.response.CategoryResponse;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.identity.authorization.PermissionCode;
import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.pagination.ListPagination;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import com.computerstore.computer_store_backend.shared.validation.IdentifierNormalizer;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CategoryService {
    private final CategoryRepository categories;
    private final PermissionPolicy permissions;
    private final UtcTimeProvider time;

    public CategoryService(CategoryRepository categories, PermissionPolicy permissions, UtcTimeProvider time) {
        this.categories = categories;
        this.permissions = permissions;
        this.time = time;
    }

    public CategoryPageResponse list(String query, Boolean active, int page, int size) {
        permissions.requirePermission(PermissionCode.CATEGORY_READ.code());
        var pagination = ListPagination.request(page, size, "name", CategoryService::invalid);
        var search = text(query, 120, false);
        var pattern = search == null || search.isEmpty() ? null
                : "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = categories.search(pattern, active, pagination);
        return new CategoryPageResponse(result.getContent().stream().map(CategoryService::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    public CategoryResponse detail(String id) {
        permissions.requirePermission(PermissionCode.CATEGORY_READ.code());
        return response(categories.findById(id(id)).orElseThrow(CategoryService::notFound));
    }

    @Transactional
    public CategoryResponse create(CategoryWriteRequest request) {
        permissions.requirePermission(PermissionCode.CATEGORY_CREATE.code());
        var values = values(request);
        rejectDuplicate(values.code(), null);
        // Follow existing bootstrap construction without changing the Entity's protected constructor.
        var category = BeanUtils.instantiateClass(Category.class);
        category.setCode(values.code());
        category.setName(values.name());
        category.setDescription(values.description());
        category.setActive(true);
        var now = time.now();
        category.setCreatedAt(now);
        category.setUpdatedAt(now);
        return response(save(category));
    }

    @Transactional
    public CategoryResponse update(String id, CategoryWriteRequest request) {
        permissions.requirePermission(PermissionCode.CATEGORY_UPDATE.code());
        var values = values(request);
        var category = locked(id);
        rejectDuplicate(values.code(), category.getId());
        category.setCode(values.code());
        category.setName(values.name());
        category.setDescription(values.description());
        category.setUpdatedAt(time.now());
        // active/createdAt are deliberately untouched by the information update.
        return response(save(category));
    }

    @Transactional
    public CategoryResponse deactivate(String id) {
        permissions.requirePermission(PermissionCode.CATEGORY_DEACTIVATE.code());
        var category = locked(id);
        if (Boolean.FALSE.equals(category.getActive())) return response(category);
        if (!Boolean.TRUE.equals(category.getActive())) throw conflict("Category state is invalid.");
        if (!categories.findProductReferencesForDeactivation(category.getId(), PageRequest.of(0, 1)).isEmpty()) {
            // Conservative restriction until the policy for referenced categories is explicitly decided.
            throw conflict("Category deactivation is unavailable while products reference it.");
        }
        category.setActive(false);
        category.setUpdatedAt(time.now());
        return response(save(category));
    }

    private Category locked(String id) {
        return categories.findForUpdate(id(id)).orElseThrow(CategoryService::notFound);
    }

    private void rejectDuplicate(String code, Long ownId) {
        categories.findByCode(code).filter(other -> ownId == null || !Objects.equals(other.getId(), ownId))
                .ifPresent(other -> { throw conflict("A category with this code already exists."); });
    }

    private Category save(Category category) {
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException failure) {
            // Do not turn unrelated FK/NOT NULL/infrastructure failures into a misleading duplicate error.
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    var name = violation.getConstraintName();
                    if ("uq_category_code".equals(name) || (name != null && name.endsWith(".uq_category_code"))) {
                        throw conflict("A category with this code already exists.");
                    }
                }
            }
            throw failure;
        }
    }

    private static CategoryWriteRequest values(CategoryWriteRequest request) {
        if (request == null) throw invalid();
        var code = IdentifierNormalizer.businessCode(request.code());
        if (code == null || code.isEmpty() || code.length() > 50) throw invalid();
        return new CategoryWriteRequest(code, text(request.name(), 120, true), text(request.description(), 500, false));
    }

    private static String text(String value, int maxCharacters, boolean required) {
        if (value == null) {
            if (required) throw invalid();
            return null;
        }
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
        var normalized = value.strip();
        if ((required && normalized.isBlank()) || normalized.codePointCount(0, normalized.length()) > maxCharacters) {
            throw invalid();
        }
        return normalized;
    }

    private static Long id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.valueOf(value); }
        catch (NumberFormatException invalid) { throw invalid(); }
    }

    private static CategoryResponse response(Category category) {
        return new CategoryResponse(category.getId().toString(), category.getCode(), category.getName(),
                category.getDescription(), Boolean.TRUE.equals(category.getActive()),
                category.getCreatedAt().toInstant(ZoneOffset.UTC), category.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid category request.");
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "The category was not found.");
    }

    private static BusinessException conflict(String detail) {
        return new BusinessException(ErrorCode.BUSINESS_CONFLICT, detail);
    }
}
