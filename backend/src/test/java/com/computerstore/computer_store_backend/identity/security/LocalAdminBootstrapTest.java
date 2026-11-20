package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapRequest;
import com.computerstore.computer_store_backend.identity.bootstrap.AdminBootstrapService;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LocalAdminBootstrapTest {
    private final LocalAdminBootstrap.Input input = mock(LocalAdminBootstrap.Input.class);
    private final LocalAdminBootstrap.Action action = mock(LocalAdminBootstrap.Action.class);
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8);
    private final String password = UUID.randomUUID().toString();
    private final Map<String, String> environment = Map.of("DB_URL", "jdbc:mysql://127.0.0.1:3306/computer_store_dev",
            "DB_USERNAME", "local_operator", "DB_PASSWORD", UUID.randomUUID().toString());

    @BeforeEach void setup() {
        when(input.line(startsWith("Confirm target"))).thenReturn("computer_store_dev");
        when(input.line(startsWith("CSM username"))).thenReturn("operator");
        when(input.line(startsWith("Employee full name"))).thenReturn("Nguyễn Văn An");
        when(input.password(anyString())).thenAnswer(invocation -> password.toCharArray());
        when(action.create(any(), any())).thenReturn(AdminBootstrapService.Result.CREATED);
    }

    @Test void defaultDisabledDoesNotPromptOrAccessPersistence() {
        assertEquals(0, run(new String[0], input));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("disabled"));
        verifyNoInteractions(input, action);
    }

    @Test void rejectsArgumentsContainingSecretsOrUnsupportedFlagsWithoutEcho() {
        for (String[] args : new String[][]{{"--bootstrap-admin", password}, {"--password=" + password}, {"--other"}}) {
            assertEquals(1, run(args, input));
        }
        verifyNoInteractions(input, action);
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains(password));
    }

    @Test void interactiveConsoleIsRequiredNoRedirectedInputFallback() {
        assertEquals(1, run(new String[]{"--bootstrap-admin"}, null));
        verifyNoInteractions(input, action);
    }

    @Test void validatesEnvironmentBeforePromptingOrConnecting() {
        assertEquals(1, LocalAdminBootstrap.run(new String[]{"--bootstrap-admin"}, input, Map.of(), action, output));
        verifyNoInteractions(input, action);
    }

    @Test void explicitDatabaseConfirmationIsRequired() {
        when(input.line(startsWith("Confirm target"))).thenReturn("computer_store");
        assertEquals(1, run(new String[]{"--bootstrap-admin"}, input));
        verifyNoInteractions(action);
        verify(input, never()).password(anyString());
    }

    @Test void mismatchedOrCancelledPasswordDoesNotConnect() {
        when(input.password(startsWith("Confirm CSM"))).thenReturn(UUID.randomUUID().toString().toCharArray());
        assertEquals(1, run(new String[]{"--bootstrap-admin"}, input));
        when(input.password(startsWith("Confirm CSM"))).thenReturn(null);
        assertEquals(1, run(new String[]{"--bootstrap-admin"}, input));
        verifyNoInteractions(action);
    }

    @Test void refusesReusingMysqlPasswordAsCsmPassword() {
        when(input.password(anyString())).thenAnswer(invocation -> environment.get("DB_PASSWORD").toCharArray());
        assertEquals(1, run(new String[]{"--bootstrap-admin"}, input));
        verifyNoInteractions(action);
    }

    @Test void successfulCommandErasesOwnedPasswordArraysAndRequest() {
        char[] first = password.toCharArray(), second = password.toCharArray();
        when(input.password(startsWith("CSM password"))).thenReturn(first);
        when(input.password(startsWith("Confirm CSM"))).thenReturn(second);
        var captured = new java.util.concurrent.atomic.AtomicReference<AdminBootstrapRequest>();
        when(action.create(any(), any())).thenAnswer(invocation -> {
            captured.set(invocation.getArgument(1));
            assertTrue(captured.get().passwordEquals(password));
            return AdminBootstrapService.Result.CREATED;
        });
        assertEquals(0, run(new String[]{"--bootstrap-admin"}, input));
        assertArrayEquals(new char[first.length], first);
        assertArrayEquals(new char[second.length], second);
        assertFalse(captured.get().passwordEquals(password));
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains("committed"));
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains(password));
    }

    @Test void failureNeverPrintsOrLogsPasswordHashOrExceptionInternals() {
        String hash = new BCryptPasswordEncoder().encode(password);
        when(action.create(any(), any())).thenThrow(new IllegalStateException(password + " " + hash));
        var logging = (ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory();
        var root = logging.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var events = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        events.start(); root.addAppender(events);
        try {
            assertEquals(1, run(new String[]{"--bootstrap-admin"}, input));
            String printed = bytes.toString(StandardCharsets.UTF_8);
            assertFalse(printed.contains(password)); assertFalse(printed.contains(hash));
            assertFalse(printed.contains("IllegalStateException")); assertFalse(printed.contains("committed"));
            assertTrue(events.list.isEmpty());
        } finally {
            root.detachAppender(events); events.stop();
        }
    }

    @Test void ordinaryApplicationScanFindsNoBootstrapBeansOrHttpEndpoints() {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        var beans = scanner.findCandidateComponents("com.computerstore.computer_store_backend");
        assertTrue(beans.stream().noneMatch(bean -> bean.getBeanClassName().contains("Bootstrap")));
        assertEquals(0, LocalAdminBootstrap.class.getAnnotations().length);
        assertEquals(0, AdminBootstrapService.class.getAnnotations().length);
    }

    private int run(String[] args, LocalAdminBootstrap.Input console) {
        return LocalAdminBootstrap.run(args, console, environment, action, output);
    }
}
