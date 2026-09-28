package com.carex.leave.support;

import com.carex.leave.auth.CurrentUser;
import com.carex.leave.org.AppUser;
import com.carex.leave.org.Role;
import com.carex.leave.org.Team;
import com.carex.leave.org.TeamRepository;
import com.carex.leave.org.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The blueprint's demo org (implementation.md §6.1):
 * Leadership (mgr Dev; members Dev, Meera, Hema) · Engineering (mgr Meera; 6 employees incl. mid-year Farhan)
 * · People Ops (mgr Hema; Harish, Isha).
 */
public class TestOrg {
    public static final String PASSWORD = "Test-Password-1";

    @Autowired private UserRepository users;
    @Autowired private TeamRepository teams;
    @Autowired private PasswordEncoder encoder;

    public CurrentUser dev, meera, hema, arjun, bala, chitra, divya, esha, farhan, harish, isha;
    public Team leadership, engineering, people;

    public void create() {
        String hash = encoder.encode(PASSWORD);
        leadership = teams.save(new Team("Leadership", new BigDecimal("50.00"), 2));
        engineering = teams.save(new Team("Engineering", new BigDecimal("30.00"), 2));
        people = teams.save(new Team("People Ops", new BigDecimal("40.00"), 2));
        dev = u("dev", Role.MANAGER, leadership, "2019-04-01", hash);
        meera = u("meera", Role.MANAGER, leadership, "2020-06-15", hash);
        hema = u("hema", Role.HR, leadership, "2018-01-10", hash);
        arjun = u("arjun", Role.EMPLOYEE, engineering, "2021-03-01", hash);
        bala = u("bala", Role.EMPLOYEE, engineering, "2022-08-10", hash);
        chitra = u("chitra", Role.EMPLOYEE, engineering, "2023-01-16", hash);
        divya = u("divya", Role.EMPLOYEE, engineering, "2024-02-01", hash);
        esha = u("esha", Role.EMPLOYEE, engineering, "2025-05-05", hash);
        farhan = u("farhan", Role.EMPLOYEE, engineering, "2026-07-20", hash);
        harish = u("harish", Role.HR, people, "2021-11-01", hash);
        isha = u("isha", Role.HR, people, "2023-06-01", hash);
        leadership.setManagerId(dev.id());
        engineering.setManagerId(meera.id());
        people.setManagerId(hema.id());
        teams.save(leadership);
        teams.save(engineering);
        teams.save(people);
    }

    private CurrentUser u(String key, Role role, Team team, String joined, String hash) {
        String name = Character.toUpperCase(key.charAt(0)) + key.substring(1);
        AppUser saved = users.save(new AppUser(key + "@test.carex.app", name, hash, role, team.getId(),
                LocalDate.parse(joined), "en-IN"));
        return CurrentUser.of(saved);
    }
}
