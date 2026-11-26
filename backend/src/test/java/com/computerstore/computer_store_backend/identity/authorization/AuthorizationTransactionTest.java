package com.computerstore.computer_store_backend.identity.authorization;

import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.security.EmployeePrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Spring transactional proxies and a resource-free transaction manager, never a datasource/Boot context. */
@SpringJUnitConfig(AuthorizationTransactionTest.Fixture.class)
class AuthorizationTransactionTest {
    @Autowired RoleResolutionService resolver;
    @Autowired LocalTransactions transactions;
    @Autowired ApplicationContext context;

    @BeforeEach void setup() { transactions.begins = 0; transactions.commits = 0; transactions.expectedReadOnly = true; signIn(); }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void activeAccountAndAllScalarReadsShareOneReadOnlyTransactionAndReturnDetachedValues() {
        var access = resolver.resolveCurrent();
        assertEquals(new CurrentActor(42L, "employee"), access.actor());
        assertEquals(Set.of(EmployeeRole.MANAGER), access.roles());
        assertEquals(Set.of("FIXTURE_PERMISSION"), access.permissionCodes());
        assertEquals(1, transactions.begins); assertEquals(1, transactions.commits);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertTrue(context.getBeansOfType(javax.sql.DataSource.class).isEmpty());
    }

    @Test void joinsAnExistingWriteTransactionWithoutOpeningANewOneOrChangingItsReadOnlyPolicy() {
        transactions.expectedReadOnly = false;
        var access = new TransactionTemplate(transactions).execute(status -> resolver.resolveCurrent());
        assertNotNull(access); assertEquals(Set.of(EmployeeRole.MANAGER), access.roles());
        assertEquals(1, transactions.begins); assertEquals(1, transactions.commits);
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private void signIn() {
        var principal = mock(EmployeePrincipal.class);
        when(principal.getUserId()).thenReturn(42L); when(principal.getUsername()).thenReturn("employee");
        when(principal.isEnabled()).thenReturn(true);
        when(principal.getSessionVersion()).thenReturn(0L);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, List.of()));
    }

    private static void assertTransaction(boolean expectedReadOnly) {
        assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        assertEquals(expectedReadOnly, TransactionSynchronizationManager.isCurrentTransactionReadOnly());
    }

    @Configuration(proxyBeanMethods = false) @EnableTransactionManagement
    @Import({CurrentActorService.class, RoleResolutionService.class})
    static class Fixture {
        @Bean LocalTransactions transactionManager() { return new LocalTransactions(); }
        @Bean AppUserRepository users(LocalTransactions transactions) {
            var repository = mock(AppUserRepository.class); var user = mock(AppUser.class);
            when(user.getUsername()).thenReturn("employee"); when(user.getActive()).thenReturn(true);
            when(repository.findById(42L)).thenAnswer(invocation -> { assertTransaction(transactions.expectedReadOnly); return Optional.of(user); });
            return repository;
        }
        @Bean EntityManager entities(LocalTransactions transactions) {
            var entities = mock(EntityManager.class);
            @SuppressWarnings("unchecked") TypedQuery<String> roles = mock(TypedQuery.class);
            @SuppressWarnings("unchecked") TypedQuery<String> permissions = mock(TypedQuery.class);
            when(entities.createQuery(RoleResolutionService.ROLES_QUERY, String.class)).thenReturn(roles);
            when(entities.createQuery(RoleResolutionService.PERMISSIONS_QUERY, String.class)).thenReturn(permissions);
            when(roles.setParameter("sessionVersion", 0L)).thenReturn(roles);
            when(roles.setParameter("userId", 42L)).thenReturn(roles);
            when(roles.getResultList()).thenAnswer(invocation -> { assertTransaction(transactions.expectedReadOnly); return List.of("MANAGER"); });
            when(permissions.setParameter("sessionVersion", 0L)).thenReturn(permissions);
            when(permissions.setParameter("userId", 42L)).thenReturn(permissions);
            when(permissions.setParameter(eq("roleCodes"), any())).thenReturn(permissions);
            when(permissions.getResultList()).thenAnswer(invocation -> { assertTransaction(transactions.expectedReadOnly); return List.of("FIXTURE_PERMISSION"); });
            return entities;
        }
    }

    static class LocalTransactions extends AbstractPlatformTransactionManager {
        private final ThreadLocal<Marker> current = new ThreadLocal<>();
        int begins, commits;
        boolean expectedReadOnly = true;
        static class Marker { boolean active; }
        @Override protected Object doGetTransaction() { return current.get() == null ? new Marker() : current.get(); }
        @Override protected boolean isExistingTransaction(Object transaction) { return ((Marker) transaction).active; }
        @Override protected void doBegin(Object transaction, TransactionDefinition definition) {
            assertEquals(expectedReadOnly, definition.isReadOnly()); ((Marker) transaction).active = true; current.set((Marker) transaction); begins++;
        }
        @Override protected void doCommit(DefaultTransactionStatus status) { commits++; }
        @Override protected void doRollback(DefaultTransactionStatus status) { }
        @Override protected void doCleanupAfterCompletion(Object transaction) { ((Marker) transaction).active = false; current.remove(); }
    }
}
