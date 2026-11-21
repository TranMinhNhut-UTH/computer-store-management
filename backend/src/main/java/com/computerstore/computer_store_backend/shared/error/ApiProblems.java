package com.computerstore.computer_store_backend.shared.error;

import java.net.URI;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Independent of MVC so later security handlers can use the same response shape. */
public final class ApiProblems {
    private ApiProblems() { }

    public static ProblemDetail create(ErrorCode code, String publicDetail, URI instance) {
        return create(code.status(), code.name(), publicDetail, instance);
    }

    /** Caller supplies a stable code and public-safe detail, including future security responses. */
    public static ProblemDetail create(HttpStatusCode status, String code, String detail, URI instance) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("about:blank"));
        problem.setProperty("code", code);
        if (instance != null) problem.setInstance(instance);
        return problem;
    }
}
