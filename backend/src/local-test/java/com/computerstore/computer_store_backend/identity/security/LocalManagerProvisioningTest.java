package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapRequest;
import com.computerstore.computer_store_backend.identity.entity.AppRole;
import com.computerstore.computer_store_backend.identity.entity.AppUser;
import com.computerstore.computer_store_backend.identity.entity.UserRole;
import com.computerstore.computer_store_backend.identity.repository.AppRoleRepository;
import com.computerstore.computer_store_backend.identity.repository.AppUserRepository;
import com.computerstore.computer_store_backend.shared.time.UtcTimeProvider;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalManagerProvisioningTest {
    private static final String[] FLAGS = {"--provision-manager", "--ack-local-development-only"};
    private static final String URL = "jdbc:mysql://127.0.0.1:3306/computer_store_dev";
    private final String dbPassword = UUID.randomUUID().toString();
    private final String password = UUID.randomUUID().toString();
    private final EntityManager entities = mock(EntityManager.class);
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final AppRoleRepository roles = mock(AppRoleRepository.class);
    private final AppRole managerRole = mock(AppRole.class);
    private final PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
    private final SimpleTransactionStatus transaction = new SimpleTransactionStatus();
    private final Query schema = mock(Query.class);
    private final Query tables = mock(Query.class);
    private final LocalManagerProvisioning.Provisioner provisioner = mock(LocalManagerProvisioning.Provisioner.class);
    private final LocalManagerProvisioning.Input input = mock(LocalManagerProvisioning.Input.class);
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();
    private final Map<String, String> env = Map.of("DB_URL", URL, "DB_USERNAME", "local-operator", "DB_PASSWORD", dbPassword);
    private final org.springframework.security.crypto.password.PasswordEncoder encoder = new SecurityConfig().passwordEncoder();

    @BeforeEach void setUp() {
        when(manager.getTransaction(any())).thenReturn(transaction);
        when(entities.createNativeQuery("SELECT DATABASE()")).thenReturn(schema);
        when(schema.getSingleResult()).thenReturn("computer_store_dev");
        when(entities.createNativeQuery(startsWith("SELECT COUNT(*) FROM information_schema.TABLES"))).thenReturn(tables);
        when(tables.getSingleResult()).thenReturn(2L);
        when(roles.findByCode("MANAGER")).thenReturn(Optional.of(managerRole));
        when(managerRole.getCode()).thenReturn("MANAGER");
    }

    @Test void createsOnlyAnActiveUserAndOneManagerAssignmentUsingLoginEncoder() {
        create();
        var user = ArgumentCaptor.forClass(AppUser.class);
        var link = ArgumentCaptor.forClass(UserRole.class);
        var order = inOrder(manager, entities);
        order.verify(manager).getTransaction(any());
        order.verify(entities).persist(user.capture());
        order.verify(entities).persist(link.capture());
        order.verify(entities).flush();
        order.verify(manager).commit(transaction);
        assertEquals("e2e-manager", user.getValue().getUsername());
        assertEquals("Local Operator", user.getValue().getFullName());
        assertTrue(user.getValue().getActive());
        assertInstanceOf(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class, encoder);
        assertTrue(encoder.matches(password, user.getValue().getPasswordHash()));
        assertSame(user.getValue(), link.getValue().getUser());
        assertSame(managerRole, link.getValue().getRole());
        assertEquals(user.getValue().getCreatedAt(), link.getValue().getAssignedAt());
        verify(roles).findByCode("MANAGER");
        verify(users).existsByUsername("e2e-manager");
        verify(entities).createNativeQuery("SELECT DATABASE()");
        verify(entities).createNativeQuery(startsWith("SELECT COUNT(*) FROM information_schema.TABLES"));
        verifyNoMoreInteractions(entities, roles, users, manager);
    }

    @Test void rejectsDuplicateWithoutWrites() {
        when(users.existsByUsername("e2e-manager")).thenReturn(true);
        assertRejectedWithoutWrites();
    }

    @Test void rejectsMissingManagerWithoutWrites() {
        when(roles.findByCode("MANAGER")).thenReturn(Optional.empty());
        assertRejectedWithoutWrites();
    }

    @Test void rejectsRoleWithWrongCodeEvenIfLookupReturnsIt() {
        when(managerRole.getCode()).thenReturn("ADMIN");
        assertRejectedWithoutWrites();
    }

    @Test void rejectsUnexpectedConnectedSchemaBeforeIdentityAccess() {
        when(schema.getSingleResult()).thenReturn("computer_store");
        assertRejectedWithoutWrites();
        verifyNoInteractions(users, roles);
    }

    @Test void rejectsNonTransactionalIdentityTables() {
        when(tables.getSingleResult()).thenReturn(1L);
        assertRejectedWithoutWrites();
        verifyNoInteractions(users, roles);
    }

    @Test void rollsBackWhenAssignmentFailsAfterUserPersist() {
        doThrow(new IllegalStateException()).when(entities).persist(isA(UserRole.class));
        assertThrows(RuntimeException.class, this::create);
        var order = inOrder(entities, manager);
        order.verify(entities).persist(isA(AppUser.class));
        order.verify(entities).persist(isA(UserRole.class));
        order.verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }

    @Test void rejectsMissingOptInAndHasNoSpringStartupHook() {
        for (String[] flags : new String[][] { {}, {FLAGS[0]}, {FLAGS[1]}, {FLAGS[0], FLAGS[0]},
                {FLAGS[0], FLAGS[1], "--role=ADMIN"} }) {
            assertEquals(1, run(flags, env));
        }
        assertEquals(1, LocalManagerProvisioning.run(FLAGS, null, env, provisioner, new PrintStream(output)));
        assertFalse(AnnotatedElementUtils.hasAnnotation(LocalManagerProvisioning.class, Component.class));
        assertFalse(ApplicationRunner.class.isAssignableFrom(LocalManagerProvisioning.class));
        assertFalse(CommandLineRunner.class.isAssignableFrom(LocalManagerProvisioning.class));
        verifyNoInteractions(input, provisioner);
    }

    @Test void rejectsEveryVariantOfTheTargetBeforePrompting() {
        for (String target : new String[] {URL.replace("127.0.0.1", "localhost"),
                URL.replace(":3306", ""), URL.replace(":3306", ":3307"), URL.replace("_dev", ""),
                URL + "?useSSL=false", URL.replace("127.0.0.1", "192.0.2.10")}) {
            assertEquals(1, run(FLAGS, Map.of("DB_URL", target)));
        }
        assertEquals(1, run(FLAGS, Map.of()));
        assertEquals(1, run(FLAGS, Map.of("DB_URL", URL)));
        verifyNoInteractions(input, provisioner);
    }

    @Test void rejectsMissingAccountFieldsBeforeProvisioning() {
        when(input.password(anyString())).thenAnswer(call -> password.toCharArray());
        assertEquals(1, run(FLAGS, env));
        when(input.line(startsWith("CSM username"))).thenReturn("e2e-manager");
        assertEquals(1, run(FLAGS, env));
        when(input.line(startsWith("Employee full name"))).thenReturn("Local Operator");
        when(input.password(anyString())).thenReturn(null);
        assertEquals(1, run(FLAGS, env));
        verifyNoInteractions(provisioner);
    }

    @Test void redactsFailureAndErasesTerminalPasswordBuffers() {
        char[] first = password.toCharArray();
        char[] second = first.clone();
        when(input.line(anyString())).thenReturn("e2e-manager", "Local Operator");
        when(input.password(anyString())).thenReturn(first, second);
        doThrow(new IllegalStateException(password + dbPassword)).when(provisioner).create(any(), any());
        assertEquals(1, run(FLAGS, env));
        verify(provisioner).create(any(), any());
        assertFalse(output.toString().contains(password));
        assertFalse(output.toString().contains(dbPassword));
        assertArrayEquals(new char[first.length], first);
        assertArrayEquals(new char[second.length], second);
    }

    private int run(String[] flags, Map<String, String> environment) {
        return LocalManagerProvisioning.run(flags, input, environment, provisioner, new PrintStream(output));
    }

    private void create() {
        try (var request = new AdminBootstrapRequest(" E2E-Manager ", " Local Operator ", password.toCharArray())) {
            LocalManagerProvisioning.create(request, users, roles, entities, encoder,
                    new UtcTimeProvider(Clock.systemUTC()), manager);
        }
    }

    private void assertRejectedWithoutWrites() {
        assertThrows(RuntimeException.class, this::create);
        verify(entities, never()).persist(any());
        verify(entities, never()).flush();
        verify(manager).rollback(transaction);
        verify(manager, never()).commit(any());
    }
}
