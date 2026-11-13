package com.example.vstock.global.security;

import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;

public class CustomUserDetails extends User {

    private final Long userId;

    public CustomUserDetails(Long userId, String email, String password) {
        super(email, password, AuthorityUtils.createAuthorityList("ROLE_USER"));
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
    }
}
