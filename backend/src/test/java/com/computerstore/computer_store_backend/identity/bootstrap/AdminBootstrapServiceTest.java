package com.computerstore.computer_store_backend.identity.bootstrap;

import com.computerstore.computer_store_backend.identity.entity.AppRole;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.entity.UserRole;
import com.computerstore.computer_store_backend.identity.repository.AppRoleRepository;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.identity.repository.UserRoleRepository;
import com.computerstore.computer_store_backend.identity.security.EmployeeAuthenticationProvider;
import com.computerstore.computer_store_backend.identity.security.EmployeeUserDetailsService;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapException.Reason.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transaction template and BCrypt; all persistence/transaction-manager collaborators are mocks. */
class AdminBootstrapServiceTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppRoleRepository roles = mock(AppRoleRepository.class);
    private final UserRoleRepository userRoles = mock(UserRoleRepository.class);
    private final EntityManager entities = mock(EntityManager.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final AppRole admin = mock(AppRole.class);
    private final Query database = mock(Query.class);
    @SuppressWarnings("unchecked")
    private final TypedQuery<Long> assignments = mock(TypedQuery.class);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final String password = UUID.randomUUID().toString();
    private final AdminBootstrapRequest input = new AdminBootstrapRequest(" Operator ", " Nguyễn Văn An ", password.toCharArray());
    private AdminBootstrapService service;

    @BeforeEach void setup() {
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(entities.createNativeQuery("SELECT DATABASE()")).thenReturn(database);
        when(database.getSingleResult()).thenReturn("computer_store_dev");
        when(roles.findByCode("ADMIN")).thenReturn(Optional.of(admin));
        when(admin.getId()).thenReturn(7L);
        when(entities.createQuery("select ur.id from UserRole ur where ur.role.id = :roleId", Long.class)).thenReturn(assignments);
        when(assignments.setParameter("roleId", 7L)).thenReturn(assignments);
        when(assignments.setMaxResults(1)).thenReturn(assignments);
        when(assignments.getResultList()).thenReturn(List.of());
        when(users.saveAndFlush(any())).thenAnswer(invocation -> {
            AppUser user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 9L); // Simulate generated ID; no database involved.
            return user;
        });
        var clock = Clock.fixed(Instant.parse("2026-11-20T01:02:03.123456789Z"), ZoneOffset.UTC);
        service = new AdminBootstrapService(users, roles, userRoles, entities, encoder, new UtcTimeProvider(clock), manager);
    }

    @AfterEach void erase() { input.close(); }

    @Test void disabledDoesNotReadOrWriteDatabase() {
        assertEquals(AdminBootstrapService.Result.DISABLED, service.bootstrap(false, null));
        verifyNoInteractions(users, roles, userRoles, entities, manager);
    }

    @Test void createsOnlyUserAndExistingAdminLinkInOneTransactionWithBcryptAndUtc() {
        assertEquals(AdminBootstrapService.Result.CREATED, service.bootstrap(true, input));
        var userCapture = ArgumentCaptor.forClass(AppUser.class);
        var linkCapture = ArgumentCaptor.forClass(UserRole.class);
        var order = inOrder(entities, assignments, users, userRoles, manager);
        order.verify(manager).getTransaction(argThat(def -> def.getIsolationLevel() == TransactionDefinition.ISOLATION_READ_COMMITTED
                && def.getTimeout() == 30));
        order.verify(entities).lock(admin, LockModeType.PESSIMISTIC_WRITE, Map.of("jakarta.persistence.lock.timeout", 5000));
        order.verify(assignments).getResultList();
        order.verify(users).existsByUsername("operator");
        order.verify(users).saveAndFlush(userCapture.capture());
        order.verify(userRoles).saveAndFlush(linkCapture.capture());
        order.verify(manager).commit(transaction);
        verify(manager, never()).rollback(any());
        var user = userCapture.getValue();
        assertEquals("operator", user.getUsername());
        assertEquals("Nguyễn Văn An", user.getFullName());
        assertTrue(user.getActive());
        assertNull(user.getEmail()); assertNull(user.getPhone()); assertNull(user.getLastLoginAt());
        assertNotEquals(password, user.getPasswordHash());
        assertTrue(user.getPasswordHash().matches("\\$2[aby]\\$.*"));
        assertTrue(encoder.matches(password, user.getPasswordHash()));
        assertEquals("2026-11-20T01:02:03.123456", user.getCreatedAt().toString());
        assertEquals(user.getCreatedAt(), user.getUpdatedAt());
        assertSame(user, linkCapture.getValue().getUser());
        assertSame(admin, linkCapture.getValue().getRole());
        assertEquals(user.getCreatedAt(), linkCapture.getValue().getAssignedAt());
        verify(roles, never()).save(any());
        verifyNoMoreInteractions(users, userRoles);
    }

    @Test void resultingAccountIsCompatibleWithRealAuthenticationProviderWithoutGrantingAuthorities() {
        service.bootstrap(true, input);
        var saved = ArgumentCaptor.forClass(AppUser.class);
        verify(users).saveAndFlush(saved.capture());
        when(users.findByUsername("operator")).thenReturn(Optional.of(saved.getValue()));
        var provider = new EmployeeAuthenticationProvider(new EmployeeUserDetailsService(users), encoder);
        var authentication = provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated("operator", password));
        assertTrue(authentication.isAuthenticated());
        assertTrue(authentication.getAuthorities().isEmpty());
        assertNull(authentication.getCredentials());
    }

    @Test void anyExistingAdminAssignmentIncludingInactiveAccountStopsBootstrap() {
        when(assignments.getResultList()).thenReturn(List.of(42L));
        assertReason(ADMIN_ALREADY_EXISTS);
        verifyNoInteractions(users, userRoles);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test void existingUsernameIsNeverPromotedOrUpdated() {
        when(users.existsByUsername("operator")).thenReturn(true);
        assertReason(USERNAME_EXISTS);
        verify(users).existsByUsername("operator");
        verifyNoMoreInteractions(users);
        verifyNoInteractions(userRoles);
        verify(manager).rollback(transaction);
    }

    @Test void missingSeededRoleDoesNotCreateIt() {
        when(roles.findByCode("ADMIN")).thenReturn(Optional.empty());
        assertReason(ADMIN_ROLE_MISSING);
        verifyNoInteractions(users, userRoles);
        verify(entities, never()).lock(any(), any(), anyMap());
        verify(roles, never()).save(any());
    }

    @Test void checksActualCatalogBeforeAnyRepositoryAccess() {
        when(database.getSingleResult()).thenReturn("computer_store");
        assertReason(UNSAFE_DATABASE_TARGET);
        verifyNoInteractions(users, roles, userRoles);
        verify(manager).rollback(transaction);
    }

    @Test void lockFailureDoesNotProceedToWritesOrRetry() {
        doThrow(new IllegalStateException("internal SQL")).when(entities).lock(any(), any(), anyMap());
        assertReason(FAILED);
        verifyNoInteractions(users, userRoles);
        verify(manager).getTransaction(any());
        verify(manager).rollback(transaction);
    }

    @Test void userInsertFailureRollsBackAndDoesNotInsertLink() {
        doThrow(new IllegalStateException(password)).when(users).saveAndFlush(any());
        assertReason(FAILED);
        verifyNoInteractions(userRoles);
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test void linkFailureRollsBackWholeTransactionAfterUserInsert() {
        when(userRoles.saveAndFlush(any())).thenThrow(new IllegalStateException(password));
        assertReason(FAILED);
        verify(users).saveAndFlush(any());
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
        // Verifies rollback delegation, NOT physical MySQL rollback; runtime DB validation is still pending.
    }

    @Test void commitFailureCannotBeReportedAsCreatedAndHasNoLeakingCause() {
        doThrow(new IllegalStateException(password)).when(manager).commit(transaction);
        assertReason(FAILED);
    }

    @Test void missingInputFailsBeforeTransaction() {
        assertEquals(INVALID_INPUT, assertThrows(AdminBootstrapException.class, () -> service.bootstrap(true, null)).reason());
        verifyNoInteractions(manager, entities, users, roles, userRoles);
    }

    private void assertReason(AdminBootstrapException.Reason reason) {
        var failure = assertThrows(AdminBootstrapException.class, () -> service.bootstrap(true, input));
        assertEquals(reason, failure.reason());
        assertNull(failure.getCause());
        assertFalse(failure.toString().contains(password));
    }
}
