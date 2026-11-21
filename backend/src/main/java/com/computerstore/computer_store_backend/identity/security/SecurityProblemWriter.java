package com.computerstore.computer_store_backend.identity.security;

import com.computerstore.computer_store_backend.shared.error.ApiProblems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.http.ProblemDetail;
import tools.jackson.databind.json.JsonMapper;

/** Writes the shared ProblemDetail shape from filters, without invoking MVC error dispatch. */
final class SecurityProblemWriter {
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class).build();

    private SecurityProblemWriter() { }

    static void write(HttpServletRequest request, HttpServletResponse response,
            HttpStatus status, String code, String safeDetail) throws IOException {
        URI instance = null;
        try { instance = URI.create(request.getRequestURI()); }
        catch (IllegalArgumentException ignored) { /* Do not echo malformed request URI. */ }
        var problem = ApiProblems.create(status, code, safeDetail, instance);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        MAPPER.writeValue(response.getOutputStream(), problem);
    }
}
