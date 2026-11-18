package com.computerstore.computer_store_backend.identity.dto.response;

/** Token is the request-handler's masked value, to be sent unchanged in headerName. */
public record CsrfTokenResponse(String headerName, String token) { }
