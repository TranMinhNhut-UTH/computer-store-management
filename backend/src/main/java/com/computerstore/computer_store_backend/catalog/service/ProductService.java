package com.computerstore.computer_store_backend.catalog.service;

import com.computerstore.computer_store_backend.catalog.dto.request.ProductCreateRequest;
import com.computerstore.computer_store_backend.catalog.dto.request.ProductPriceRequest;
import com.computerstore.computer_store_backend.catalog.dto.request.ProductWriteRequest;
import com.computerstore.computer_store_backend.catalog.dto.response.ProductPageResponse;
import com.computerstore.computer_store_backend.catalog.dto.response.ProductResponse;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.repository.ProductRepository;
import com.computerstore.computer_store_backend.identity.authorization.PermissionPolicy;
import com.computerstore.computer_store_backend.inventory.service.InventoryLifecycleService;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.pagination.ListPagination;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import com.computerstore.computer_store_backend.shared.validation.IdentifierNormalizer;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.util.Objects;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.computerstore.computer_store_backend.identity.authorization.PermissionCode.*;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private static final String UNSIGNED_DECIMAL = "0|[1-9][0-9]{0,18}";
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final PermissionPolicy permissions;
    private final UtcTimeProvider time;
    private final EntityManager entities;
    private final InventoryLifecycleService inventory;
    private final boolean creationEnabled;

    public ProductService(ProductRepository products, CategoryRepository categories,
            PermissionPolicy permissions, UtcTimeProvider time, EntityManager entities,
            InventoryLifecycleService inventory, @Value("${csm.product-creation.enabled:true}") boolean creationEnabled) {
        this.products = products;
        this.categories = categories;
        this.permissions = permissions;
        this.time = time;
        this.entities = entities;
        this.inventory = inventory;
        this.creationEnabled = creationEnabled;
    }

    public ProductPageResponse list(String query, Boolean active, String categoryId, int page, int size) {
        var allowedActions = permissions.availableActions(PRODUCT_READ,
                PRODUCT_UPDATE, PRODUCT_PRICE_UPDATE, PRODUCT_DEACTIVATE, PRODUCT_CREATE);
        if (!creationEnabled || !allowedActions.contains(PRODUCT_PRICE_UPDATE.code())) {
            allowedActions = allowedActions.stream().filter(code -> !PRODUCT_CREATE.code().equals(code)).toList();
        }
        var pagination = ListPagination.request(page, size, "name", ProductService::invalid);
        var q = text(query, 200, false);
        var pattern = q == null || q.isEmpty() ? null :
                "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var result = products.search(pattern, active, categoryId == null ? null : id(categoryId), pagination);
        return new ProductPageResponse(result.getContent().stream().map(ProductService::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages(), allowedActions);
    }

    public ProductResponse detail(String id) {
        permissions.requirePermission(PRODUCT_READ.code());
        return response(products.findDetail(id(id)).orElseThrow(ProductService::notFound));
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest request) {
        permissions.requirePermission(PRODUCT_CREATE.code());
        // The initial price uses the same explicit boundary as every subsequent price change.
        permissions.requirePermission(PRODUCT_PRICE_UPDATE.code());
        if (request == null) throw invalid();
        var values = information(request.information());
        var amount = price(request.sellingPrice());
        if (!creationEnabled) {
            throw conflict("Product creation is currently disabled.");
        }
        // Lock the active Category before inserting Product, using the same lock as Category deactivation.
        var category = activeCategory(values.categoryId());
        rejectDuplicate(values.sku(), null);
        var product = BeanUtils.instantiateClass(Product.class);
        product.setSku(values.sku());
        product.setName(values.name());
        product.setCategory(category);
        product.setBrandName(values.brandName());
        product.setModelCode(values.modelCode());
        product.setDescription(values.description());
        product.setMinStockLevel(values.minStockLevel());
        product.setSellingPrice(amount);
        product.setActive(true);
        var now = time.now();
        product.setCreatedAt(now);
        product.setUpdatedAt(now);
        var saved = save(product);
        inventory.initializeForNewProduct(saved);
        return response(saved);
    }

    @Transactional
    public ProductResponse update(String id, ProductWriteRequest request) {
        permissions.requirePermission(PRODUCT_UPDATE.code());
        long productId = id(id);
        var values = information(request);
        // Lock Category before Product, matching the existing Category deactivation lock order.
        var category = activeCategory(values.categoryId());
        var product = locked(productId);
        rejectDuplicate(values.sku(), productId);
        product.setSku(values.sku());
        product.setName(values.name());
        product.setCategory(category);
        product.setBrandName(values.brandName());
        product.setModelCode(values.modelCode());
        product.setDescription(values.description());
        product.setMinStockLevel(values.minStockLevel());
        product.setUpdatedAt(time.now());
        return response(save(product));
    }

    @Transactional
    public ProductResponse updatePrice(String id, ProductPriceRequest request) {
        permissions.requirePermission(PRODUCT_PRICE_UPDATE.code());
        if (request == null) throw invalid();
        var amount = price(request.sellingPrice());
        var product = locked(id(id));
        product.setSellingPrice(amount);
        product.setUpdatedAt(time.now());
        return response(save(product));
    }

    @Transactional
    public ProductResponse deactivate(String id) {
        permissions.requirePermission(PRODUCT_DEACTIVATE.code());
        var product = locked(id(id));
        if (Boolean.FALSE.equals(product.getActive())) return response(product);
        product.setActive(false);
        product.setUpdatedAt(time.now());
        return response(save(product));
    }

    private Category activeCategory(long id) {
        discardCachedSnapshot(Category.class, id);
        var category = categories.findForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "The category was not found."));
        if (!Boolean.TRUE.equals(category.getActive())) throw conflict("The selected category is inactive.");
        return category;
    }

    private Product locked(long id) {
        discardCachedSnapshot(Product.class, id);
        return products.findForUpdate(id).orElseThrow(ProductService::notFound);
    }

    private void discardCachedSnapshot(Class<?> type, long id) {
        // A subsequent locking query must hydrate from the database, not reuse the managed instance.
        // getReference also works for a missing row; the locking query determines whether it exists.
        entities.detach(entities.getReference(type, id));
    }

    private void rejectDuplicate(String sku, Long ownId) {
        if (products.findBySku(sku).filter(other -> !Objects.equals(other.getId(), ownId)).isPresent()) {
            throw duplicate();
        }
    }

    private Product save(Product product) {
        try {
            return products.saveAndFlush(product);
        } catch (DataIntegrityViolationException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation) {
                    var name = violation.getConstraintName();
                    if ("uq_product_sku".equals(name) || (name != null && name.endsWith(".uq_product_sku"))) {
                        throw duplicate();
                    }
                }
            }
            throw failure;
        }
    }

    private record Information(String sku, String name, long categoryId, String brandName,
            String modelCode, String description, long minStockLevel) { }

    private static Information information(ProductWriteRequest request) {
        if (request == null) throw invalid();
        var sku = IdentifierNormalizer.businessCode(request.sku());
        if (sku == null || sku.isEmpty() || sku.length() > 64) throw invalid();
        var description = text(request.description(), 65_535, false);
        if (description != null && description.getBytes(StandardCharsets.UTF_8).length > 65_535) throw invalid();
        return new Information(sku, text(request.name(), 200, true), id(request.categoryId()),
                text(request.brandName(), 100, false), text(request.modelCode(), 100, false),
                description, unsignedLong(request.minStockLevel()));
    }

    private static String text(String value, int limit, boolean required) {
        if (value == null) {
            if (required) throw invalid();
            return null;
        }
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(value)) throw invalid();
        String trimmed = value.strip();
        if ((required && trimmed.isEmpty()) || trimmed.codePointCount(0, trimmed.length()) > limit) throw invalid();
        return trimmed;
    }

    private static long id(String value) {
        long id = unsignedLong(value);
        if (id == 0) throw invalid();
        return id;
    }

    private static long unsignedLong(String value) {
        if (value == null || !value.matches(UNSIGNED_DECIMAL)) throw invalid();
        try { return Long.parseLong(value); }
        catch (NumberFormatException ignored) { throw invalid(); }
    }

    private static BigDecimal price(String value) {
        if (value == null || !value.matches(UNSIGNED_DECIMAL)) throw invalid();
        return new BigDecimal(value);
    }

    private static ProductResponse response(Product product) {
        return new ProductResponse(product.getId().toString(), product.getSku(), product.getName(),
                product.getCategory().getId().toString(), product.getCategory().getName(), product.getBrandName(),
                product.getModelCode(), product.getDescription(), product.getSellingPrice().toPlainString(),
                product.getMinStockLevel().toString(), Boolean.TRUE.equals(product.getActive()),
                product.getCreatedAt().toInstant(ZoneOffset.UTC), product.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, "Invalid product request.");
    }

    private static BusinessException notFound() {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "The product was not found.");
    }

    private static BusinessException conflict(String detail) { return new BusinessException(ErrorCode.BUSINESS_CONFLICT, detail); }
    private static BusinessException duplicate() { return conflict("A product with this SKU already exists."); }
}
