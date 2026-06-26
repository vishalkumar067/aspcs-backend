package com.aspcs.config;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)   // runs BEFORE Spring Security
public class GlobalCorsFilter implements Filter {

    private final Set<String> allowedOrigins;

    public GlobalCorsFilter(
            @Value("${cors.allowed-origins:https://aspcspatna.ac.in,https://www.aspcspatna.ac.in,http://localhost:3000}")
            String originsRaw) {
        this.allowedOrigins = Arrays.stream(originsRaw.split(","))
                .map(String::trim)
                .collect(Collectors.toSet());
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  request  = (HttpServletRequest)  req;
        HttpServletResponse response = (HttpServletResponse) res;

        String origin = request.getHeader("Origin");

        // Set CORS headers for every allowed origin
        if (origin != null && allowedOrigins.contains(origin)) {
            response.setHeader("Access-Control-Allow-Origin",  origin);
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Authorization, Content-Type, Accept, X-Requested-With");
            response.setHeader("Access-Control-Expose-Headers","Authorization, Content-Disposition");
            response.setHeader("Access-Control-Max-Age",       "3600");
            response.setHeader("Vary",                         "Origin");
        }

        // Short-circuit OPTIONS preflight — return 200 immediately
        // Spring Security never gets to reject it
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            response.setStatus(HttpServletResponse.SC_OK);
            return;   // do NOT call chain.doFilter
        }

        chain.doFilter(req, res);
    }
}
