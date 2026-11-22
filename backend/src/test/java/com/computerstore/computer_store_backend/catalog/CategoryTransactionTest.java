package com.computerstore.computer_store_backend.catalog;

import com.computerstore.computer_store_backend.catalog.dto.request.CategoryWriteRequest;
import com.computerstore.computer_store_backend.catalog.entity.Category;
import com.computerstore.computer_store_backend.catalog.entity.Product;
import com.computerstore.computer_store_backend.catalog.repository.CategoryRepository;
import com.computerstore.computer_store_backend.catalog.service.CategoryService;
import com.computerstore.computer_store_backend.identity.authorization.*;
import com.computerstore.computer_store_backend.shared.error.BusinessException;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real service transactional proxies with no resource/datasource; not proof of physical MySQL rollback. */
@SpringJUnitConfig(CategoryTransactionTest.Fixture.class)
class CategoryTransactionTest {
    @Autowired CategoryService service;
    @Autowired CategoryRepository repository;
    @Autowired RoleResolutionService resolver;
    @Autowired Transactions transactions;
    @Autowired ApplicationContext context;

    @BeforeEach void setup() {
        reset(repository,resolver);
        transactions.begins=transactions.commits=transactions.rollbacks=0;
        transactions.expectedReadOnly=true;
        when(resolver.resolveCurrent()).thenAnswer(call -> {
            assertTransaction(transactions.expectedReadOnly);
            return new ResolvedAccess(new CurrentActor(42L,"employee"),Set.of(EmployeeRole.MANAGER),
                    Set.of("CATEGORY_READ","CATEGORY_CREATE","CATEGORY_UPDATE","CATEGORY_DEACTIVATE"));
        });
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());
        when(repository.findForUpdate(7L)).thenAnswer(call -> {
            assertTransaction(false); return Optional.of(CategoryTestData.category(7,true));
        });
        when(repository.saveAndFlush(any(Category.class))).thenAnswer(call -> {
            assertTransaction(false); Category c=call.getArgument(0);
            if(c.getId()==null) ReflectionTestUtils.setField(c,"id",7L);
            return c;
        });
        // Arranging the mocked resolver through its real transactional proxy opens a setup transaction.
        // Measure only the service invocation below, not the fixture's stubbing calls.
        transactions.begins=transactions.commits=transactions.rollbacks=0;
    }

    @Test void readGuardAndRepositoryShareReadOnlyTransaction() {
        when(repository.search(isNull(),isNull(),any())).thenAnswer(call -> {
            assertTransaction(true); return new PageImpl<Category>(List.of());
        });
        service.list(null,null,0,20);
        assertEquals(1,transactions.begins); assertEquals(1,transactions.commits);
        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    @Test void createAndUpdateRunInWriteTransactions() {
        transactions.expectedReadOnly=false;
        service.create(new CategoryWriteRequest("CPU","Processors",null));
        service.update("7",new CategoryWriteRequest("RAM","Memory",null));
        assertEquals(2,transactions.begins); assertEquals(2,transactions.commits);
    }

    @Test void referencedDeactivationRollsBackAndNeverFlushes() {
        transactions.expectedReadOnly=false;
        when(repository.findProductReferencesForDeactivation(eq(7L),any())).thenAnswer(call -> {
            assertTransaction(false); return List.of(mock(Product.class));
        });
        assertThrows(BusinessException.class, () -> service.deactivate("7"));
        assertEquals(1,transactions.rollbacks); assertEquals(0,transactions.commits);
        verify(repository,never()).saveAndFlush(any());
    }

    @Test void infrastructureFlushFailureRollsBackTheServiceTransaction() {
        transactions.expectedReadOnly=false;
        doThrow(new IllegalStateException("fixture failure")).when(repository).saveAndFlush(any());
        assertThrows(IllegalStateException.class, () -> service.create(new CategoryWriteRequest("CPU","Processors",null)));
        assertEquals(1,transactions.rollbacks); assertEquals(0,transactions.commits);
    }

    @Test void existingCallerTransactionIsJoinedWithoutRequiresNew() {
        transactions.expectedReadOnly=false;
        new TransactionTemplate(transactions).execute(status -> service.create(new CategoryWriteRequest("CPU","Processors",null)));
        assertEquals(1,transactions.begins); assertEquals(1,transactions.commits);
    }

    private static void assertTransaction(boolean readOnly) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(readOnly,TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement
    @Import({CategoryService.class,PermissionPolicy.class,AuthorizationService.class})
    static class Fixture {
        @Bean Transactions transactionManager() { return new Transactions(); }
        @Bean CategoryRepository repository() { return mock(CategoryRepository.class); }
        @Bean RoleResolutionService resolver() { return mock(RoleResolutionService.class); }
        @Bean UtcTimeProvider time() { return new UtcTimeProvider(Clock.systemUTC()); }
    }

    static class Transactions extends AbstractPlatformTransactionManager {
        final ThreadLocal<Marker> current=new ThreadLocal<>();
        int begins,commits,rollbacks; boolean expectedReadOnly;
        static class Marker { boolean active; }
        @Override protected Object doGetTransaction() { return current.get()==null?new Marker():current.get(); }
        @Override protected boolean isExistingTransaction(Object value) { return ((Marker)value).active; }
        @Override protected void doBegin(Object value,TransactionDefinition definition) {
            assertEquals(expectedReadOnly,definition.isReadOnly());
            ((Marker)value).active=true; current.set((Marker)value); begins++;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        @Override protected void doCleanupAfterCompletion(Object value) { ((Marker)value).active=false; current.remove(); }
    }
}
