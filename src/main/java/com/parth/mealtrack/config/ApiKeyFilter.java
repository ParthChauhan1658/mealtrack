package com.parth.mealtrack.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Requires X-API-Key on every write request under /api. Reads stay open. */
@Component
public class ApiKeyFilter extends OncePerRequestFilter {
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");
    private final byte[] expected;

    public ApiKeyFilter(MealtrackProperties props) {
        this.expected = props.apiKey().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/") || SAFE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String given = req.getHeader("X-API-Key");
        // An unconfigured key rejects all writes rather than leaving them open.
        boolean ok = expected.length > 0 && given != null
                && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8));
        if (ok) {
            chain.doFilter(req, res);
            return;
        }
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setContentType("application/json");
        res.getWriter().write("{\"timestamp\":\"" + Instant.now() + "\",\"status\":401,\"error\":\"Unauthorized\","
                + "\"message\":\"Missing or invalid X-API-Key\",\"path\":\"" + req.getRequestURI() + "\"}");
    }
}
