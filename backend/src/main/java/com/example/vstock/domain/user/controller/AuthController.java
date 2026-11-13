package com.example.vstock.domain.user.controller;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @GetMapping("/auth/csrf")
    public CsrfToken findCsrfToken(CsrfToken csrfToken) {
        return csrfToken;
    }
}
