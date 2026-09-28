package com.carex.leave.org;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

/** Assumption A1: MANAGER and HR users are also employees (implementation.md §14.1). */
public enum Role {
    EMPLOYEE, MANAGER, HR;

    public List<GrantedAuthority> authorities() {
        return switch (this) {
            case EMPLOYEE -> List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"));
            case MANAGER -> List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_MANAGER"));
            case HR -> List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE"), new SimpleGrantedAuthority("ROLE_HR"));
        };
    }
}
