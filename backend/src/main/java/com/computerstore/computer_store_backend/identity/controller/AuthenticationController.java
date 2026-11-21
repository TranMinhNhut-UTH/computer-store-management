package com.computerstore.computer_store_backend.identity.controller;

import com.computerstore.computer_store_backend.identity.dto.request.LoginRequest;
import com.computerstore.computer_store_backend.identity.dto.response.CurrentUserResponse;
import com.computerstore.computer_store_backend.identity.dto.response.CsrfTokenResponse;
import com.computerstore.computer_store_backend.identity.security.ApiAuthenticationEntryPoint;
import com.computerstore.computer_store_backend.identity.security.AuthenticationSessionService;
import com.computerstore.computer_store_backend.identity.security.EmployeePrincipal;
import com.computerstore.computer_store_backend.shared.error.ApiProblems;
import com.computerstore.computer_store_backend.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

/** SPA flow: GET csrf, send returned token unchanged in its named header with the session cookie.
 * Fetch csrf again after successful login and after logout; previous tokens are invalidated.
 * Only the auth DTO's decimal-string ID contract is defined here, not a global DTO decision.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthenticationController {
    private final AuthenticationSessionService sessions;
    private final ApiAuthenticationEntryPoint entryPoint;

    public AuthenticationController(AuthenticationSessionService sessions, ApiAuthenticationEntryPoint entryPoint) {
        this.sessions = sessions;
        this.entryPoint = entryPoint;
    }

    @GetMapping("/csrf")
    public CsrfTokenResponse csrf(HttpServletRequest request) {
        var token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return new CsrfTokenResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/login")
    public CurrentUserResponse login(@Valid @RequestBody LoginRequest input,
            HttpServletRequest request, HttpServletResponse response) {
        return sessions.login(input, request, response);
    }

    @GetMapping("/me")
    public CurrentUserResponse me(Authentication authentication) {
        return CurrentUserResponse.from((EmployeePrincipal) authentication.getPrincipal());
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        sessions.logout(request, response, authentication);
    }

    @ExceptionHandler(AuthenticationException.class)
    public void authenticationFailure(AuthenticationException exception,
            HttpServletRequest request, HttpServletResponse response) throws IOException {
        entryPoint.commence(request, response, exception);
    }

    @ExceptionHandler(AuthenticationServiceException.class)
    public ResponseEntity<ProblemDetail> authenticationServiceFailure(AuthenticationServiceException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(ApiProblems.create(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.detail(), null));
    }
}
