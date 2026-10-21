package com.computerstore.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giới hạn 20 request/phút/IP cho nhóm /api/auth/** (chống brute-force và spam OTP).
 * Bản in-memory đủ cho 1 instance; nếu chạy nhiều instance nên chuyển sang Redis/Bucket4j.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final int LIMIT = 20;
    private static final long WINDOW_MS = 60_000;

    private static class Window {
        long start;
        int count;
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = System.currentTimeMillis();
        if (windows.size() > 10_000) windows.clear();

        Window w = windows.computeIfAbsent(request.getRemoteAddr(), k -> new Window());
        boolean allowed;
        synchronized (w) {
            if (now - w.start > WINDOW_MS) {
                w.start = now;
                w.count = 0;
            }
            allowed = ++w.count <= LIMIT;
        }

        if (!allowed) {
            response.setStatus(429);
            response.setContentType("application/problem+json;charset=UTF-8");
            response.getWriter().write(
                    "{\"status\":429,\"title\":\"Yêu cầu không thành công\","
                            + "\"detail\":\"Bạn thao tác quá nhanh. Vui lòng thử lại sau.\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
