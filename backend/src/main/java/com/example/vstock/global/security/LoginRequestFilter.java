package com.example.vstock.global.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

final class LoginRequestFilter extends OncePerRequestFilter {

    private static final int MAX_BCRYPT_PASSWORD_BYTES = 72;

    private final RequestMatcher loginRequest;

    LoginRequestFilter(String loginPath) {
        loginRequest = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, loginPath);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !loginRequest.matches(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getQueryString() != null) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        if (!isFormContentType(request.getContentType())) {
            response.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
            return;
        }

        String password = request.getParameter(UsernamePasswordAuthenticationFilter.SPRING_SECURITY_FORM_PASSWORD_KEY);
        if (password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BCRYPT_PASSWORD_BYTES) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        chain.doFilter(request, response);
    }

    private static boolean isFormContentType(String contentType) {
        if (contentType == null) {
            return false;
        }
        try {
            return MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(MediaType.parseMediaType(contentType));
        } catch (InvalidMediaTypeException e) {
            return false;
        }
    }
}
