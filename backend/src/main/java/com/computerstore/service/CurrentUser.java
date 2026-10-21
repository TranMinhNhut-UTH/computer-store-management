package com.computerstore.service;

import com.computerstore.exception.AppException;
import com.computerstore.security.AuthUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

@Component
public class CurrentUser {

    public Optional<Long> userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthUser u) {
            return Optional.of(u.id());
        }
        return Optional.empty();
    }

    public long requireUserId() {
        return userId().orElseThrow(() -> new AppException(401, "Bạn chưa đăng nhập."));
    }

    public String ip() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            return attrs.getRequest().getRemoteAddr();
        }
        return null;
    }
}
