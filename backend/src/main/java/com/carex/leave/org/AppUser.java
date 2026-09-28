package com.carex.leave.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDate;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String email;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "joining_date", nullable = false)
    private LocalDate joiningDate;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "preferred_language", nullable = false)
    private String preferredLanguage = "en-IN";

    @Version
    private long version;

    protected AppUser() {}

    public AppUser(String email, String fullName, String passwordHash, Role role, Long teamId,
                   LocalDate joiningDate, String preferredLanguage) {
        this.email = email.toLowerCase();
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.teamId = teamId;
        this.joiningDate = joiningDate;
        this.preferredLanguage = preferredLanguage;
    }

    public Long getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public Long getTeamId() { return teamId; }
    public LocalDate getJoiningDate() { return joiningDate; }
    public boolean isActive() { return active; }
    public String getPreferredLanguage() { return preferredLanguage; }

    @Override
    public String toString() { // never includes password hash
        return "AppUser[id=" + id + ", role=" + role + "]";
    }
}
