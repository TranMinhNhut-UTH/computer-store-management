package com.computerstore.computer_store_backend.shared.error;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class ApiProblemsTest {
    @Test void createsStandardProblemWithStableExtension() {
        var problem = ApiProblems.create(ErrorCode.RESOURCE_NOT_FOUND, "Resource unavailable.", URI.create("/items/1"));
        assertEquals(URI.create("about:blank"), problem.getType());
        assertEquals("Not Found", problem.getTitle());
        assertEquals(404, problem.getStatus());
        assertEquals("Resource unavailable.", problem.getDetail());
        assertEquals(URI.create("/items/1"), problem.getInstance());
        assertEquals("RESOURCE_NOT_FOUND", problem.getProperties().get("code"));
    }

    @Test void responseFactoryIsReusableWithoutMvcOrSecurityDependencies() {
        var problem = ApiProblems.create(HttpStatus.FORBIDDEN, "FORBIDDEN", "Access denied.", null);
        assertEquals(403, problem.getStatus());
        assertEquals("FORBIDDEN", problem.getProperties().get("code"));
        assertNull(problem.getInstance());
    }
}
