package com.carex.leave.auth;

import com.carex.leave.org.AppUser;
import com.carex.leave.org.Role;

/** The authenticated principal. Always derived from the JWT + DB — never from request bodies. */
public record CurrentUser(Long id, String email, String fullName, Role role, Long teamId) {
    public static CurrentUser of(AppUser u) {
        return new CurrentUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.getTeamId());
    }

    public boolean isHr() { return role == Role.HR; }
    public boolean isManager() { return role == Role.MANAGER; }
}
