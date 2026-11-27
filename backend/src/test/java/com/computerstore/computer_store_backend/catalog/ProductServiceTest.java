package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.inventory.service.InventoryLifecycleService;

import com.computerstore.computer_store_backend.catalog.dto.request.*;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.repository.ProductRepository;
import com.computerstore.computer_store_backend.catalog.service.ProductService;
import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static com.computerstore.computer_store_backend.shared.error.ErrorCode.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductServiceTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final RoleResolutionService roles = mock(RoleResolutionService.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final Product product = BeanUtils.instantiateClass(Product.class);
    private final ProductService service;
    private final ProductWriteRequest input = new ProductWriteRequest(" it-sku ", " Product ", "7",
            " Brand ", " M1 ", null, "9223372036854775807");

    ProductServiceTest() {
        var target = new ProductService(products, categories, new PermissionPolicy(new AuthorizationService(roles)),
                new UtcTimeProvider(Clock.fixed(Instant.parse("2026-11-22T02:00:00Z"), ZoneOffset.UTC)),
                mock(jakarta.persistence.EntityManager.class),
                mock(InventoryLifecycleService.class), false);
        var proxy = new ProxyFactory(target);
        proxy.setProxyTargetClass(true);
        var txInterceptor = new TransactionInterceptor();
        txInterceptor.setTransactionManager(tx);
        txInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        proxy.addAdvice(txInterceptor);
        service = (ProductService) proxy.getProxy();
    }

    @BeforeEach void setup() {
        var category = CategoryTestData.category(7, true);
        ReflectionTestUtils.setField(product, "id", 42L);
        product.setSku("ORIGINAL"); product.setName("Original"); product.setCategory(category);
        product.setSellingPrice(new BigDecimal("9007199254740993")); product.setMinStockLevel(2L);
        product.setCreatedAt(CategoryTestData.CREATED); product.setUpdatedAt(CategoryTestData.CREATED);
        when(products.findForUpdate(42L)).thenReturn(Optional.of(product));
        when(products.findDetail(42L)).thenReturn(Optional.of(product));
        when(products.saveAndFlush(product)).thenReturn(product);
        when(categories.findForUpdate(7L)).thenReturn(Optional.of(category));
        when(tx.getTransaction(any())).thenReturn(transaction);
        grants("PRODUCT_READ", "PRODUCT_CREATE", "PRODUCT_UPDATE", "PRODUCT_PRICE_UPDATE", "PRODUCT_DEACTIVATE");
    }

    private void grants(String... grants) {
        when(roles.resolveCurrent()).thenReturn(new ResolvedAccess(new CurrentActor(1L, "employee"),
                Set.of(EmployeeRole.MANAGER), Set.of(grants)));
    }

    @Test void listEscapesSearchAndUsesStatusCategoryAndStablePagination() {
        var page = PageRequest.of(1, 20, Sort.by("name", "id"));
        when(products.search("%x!%!_!!%", false, 7L, page)).thenReturn(new PageImpl<>(List.of(product), page, 21));
        var result = service.list(" x%_! ", false, "7", 1, 20);
        assertEquals(1, result.page());
        assertEquals(21L, result.totalElements());
        assertEquals("42", result.items().getFirst().id());
        assertEquals("9007199254740993", result.items().getFirst().sellingPrice());
        verify(tx).getTransaction(argThat(def -> def.isReadOnly()));
    }

    @Test void detailUsesDtoAndUnknownIdIs404() {
        assertEquals("7", service.detail("42").categoryId());
        assertCode(RESOURCE_NOT_FOUND, () -> service.detail("99"));
    }

    @Test void updateNormalizesAndLocksCategoryFirstWithoutChangingPriceOrActive() {
        grants("PRODUCT_UPDATE");
        var changed = service.update("42", input);
        assertEquals("IT-SKU", changed.sku());
        assertEquals("9223372036854775807", changed.minStockLevel());
        assertEquals("9007199254740993", changed.sellingPrice());
        assertTrue(changed.active());
        assertEquals(CategoryTestData.CREATED.toInstant(ZoneOffset.UTC), changed.createdAt());
        var order = inOrder(categories, products);
        order.verify(categories).findForUpdate(7L);
        order.verify(products).findForUpdate(42L);
        order.verify(products).saveAndFlush(product);
    }

    @Test void rejectsDuplicateSkuBeforeWriting() {
        var other = BeanUtils.instantiateClass(Product.class);
        ReflectionTestUtils.setField(other, "id", 99L);
        when(products.findBySku("IT-SKU")).thenReturn(Optional.of(other));
        assertCode(BUSINESS_CONFLICT, () -> service.update("42", input));
        verify(products, never()).saveAndFlush(any());
    }

    @Test void rejectsInactiveOrMissingCategoryBeforeLockingProduct() {
        when(categories.findForUpdate(7L)).thenReturn(Optional.of(CategoryTestData.category(7, false)));
        assertCode(BUSINESS_CONFLICT, () -> service.update("42", input));
        when(categories.findForUpdate(7L)).thenReturn(Optional.empty());
        assertCode(RESOURCE_NOT_FOUND, () -> service.update("42", input));
        verifyNoInteractions(products);
    }

    @Test void createRequiresInitialPriceGrantAndHonorsExplicitDisable() {
        grants("PRODUCT_CREATE");
        assertCode(ACCESS_DENIED, () -> service.create(new ProductCreateRequest(input, "1")));
        verifyNoInteractions(products, categories);
        grants("PRODUCT_CREATE", "PRODUCT_PRICE_UPDATE");
        var failure = assertThrows(BusinessException.class, () -> service.create(new ProductCreateRequest(input, "1")));
        assertEquals(BUSINESS_CONFLICT, failure.code());
        assertEquals("Product creation is currently disabled.", failure.getMessage());
        verify(products, never()).saveAndFlush(any());
        verify(tx, times(2)).rollback(transaction);
        verify(tx, never()).commit(any());
        verify(tx, times(2)).getTransaction(argThat(def -> !def.isReadOnly()));
    }

    @Test void priceRequiresDistinctPermissionAndPreservesNineteenDigits() {
        grants("PRODUCT_UPDATE");
        assertCode(ACCESS_DENIED, () -> service.updatePrice("42", new ProductPriceRequest("9999999999999999999")));
        verifyNoInteractions(products);
        grants("PRODUCT_PRICE_UPDATE");
        assertEquals("9999999999999999999", service.updatePrice("42", new ProductPriceRequest("9999999999999999999")).sellingPrice());
        assertEquals("ORIGINAL", product.getSku());
    }

    @ParameterizedTest @ValueSource(strings = {"-1", "1.2", "01", "1e2", "10000000000000000000"})
    void invalidPriceDoesNotReachPersistence(String price) {
        assertCode(VALIDATION_ERROR, () -> service.updatePrice("42", new ProductPriceRequest(price)));
        verifyNoInteractions(products, categories);
    }

    @Test void rejectsInvalidPaginationAndSignedBigintOverflow() {
        assertCode(VALIDATION_ERROR, () -> service.list(null, null, null, 0, 101));
        assertCode(VALIDATION_ERROR, () -> service.detail("9223372036854775808"));
        assertCode(VALIDATION_ERROR, () -> service.update("42", new ProductWriteRequest("SKU", "Name", "7",
                null, null, null, "9223372036854775808")));
        verifyNoInteractions(products, categories);
    }

    @Test void deactivationIsNonDestructiveAndIdempotent() {
        assertFalse(service.deactivate("42").active());
        assertFalse(service.deactivate("42").active());
        verify(products).saveAndFlush(product);
        verify(products, never()).delete(any());
        verifyNoInteractions(categories);
    }

    @Test void flushFailureRollsBackWithoutClaimingItWasADuplicate() {
        var failure = new DataIntegrityViolationException("unclassified constraint");
        when(products.saveAndFlush(product)).thenThrow(failure);
        assertSame(failure, assertThrows(DataIntegrityViolationException.class,
                () -> service.updatePrice("42", new ProductPriceRequest("1"))));
        verify(tx).rollback(transaction);
        verify(tx, never()).commit(any());
    }

    @Test void noReadsOrWritesWithoutExplicitPermissions() {
        grants();
        assertCode(ACCESS_DENIED, () -> service.list(null, null, null, 0, 20));
        assertCode(ACCESS_DENIED, () -> service.update("42", input));
        grants("PRODUCT_READ");
        assertCode(ACCESS_DENIED, () -> service.deactivate("42"));
        verifyNoInteractions(products, categories);
    }

    private static void assertCode(ErrorCode code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).code());
    }
}