package com.webmars.webmars_api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final Map<String, List<Instant>> loginAttempts = new ConcurrentHashMap<>();
    private final Map<String, List<Instant>> registerAttempts = new ConcurrentHashMap<>();

    private static final int LOGIN_LIMIT = 5;
    private static final int REGISTER_LIMIT = 10;
    private static final long LOGIN_WINDOW_SECONDS = 15 * 60;
    private static final long REGISTER_WINDOW_SECONDS = 60 * 60;

    private boolean isRateLimited(Map<String, List<Instant>> attempts, String key, int limit, long windowSeconds){
        Instant now = Instant.now();
        Instant windowStart = now.minusSeconds(windowSeconds);
        attempts.putIfAbsent(key, new ArrayList<>());
        List<Instant> timestamps = attempts.get(key);
        synchronized (timestamps) {
            timestamps.removeIf(t -> t.isBefore(windowStart));
            if (timestamps.size() >= limit){
                return true;
            }
            timestamps.add(now);
            return false;
        }
    }
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)throws ServletException, IOException {
        String path = request.getRequestURI();
        String ip = request.getRemoteAddr();

        if (path.equals("/auth/login")) {
            if (isRateLimited(loginAttempts, ip, LOGIN_LIMIT, LOGIN_WINDOW_SECONDS)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many login attempts. Try again in 15 minutes.\"}");
                return;
            }
        }
        if (path.equals("/auth/register")) {
            if (isRateLimited(registerAttempts, ip, REGISTER_LIMIT, REGISTER_WINDOW_SECONDS)) {
                response.setStatus(429);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Too many registration attempts. Try again in 1 hour.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
