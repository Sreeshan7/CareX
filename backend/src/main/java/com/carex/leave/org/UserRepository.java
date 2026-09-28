package com.carex.leave.org;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByEmail(String email);

    List<AppUser> findByTeamIdAndActiveTrueOrderByFullName(Long teamId);

    List<AppUser> findByRoleAndActiveTrueOrderById(Role role);

    /** HR head = an HR user who manages a team (seed: Hema manages People Ops). */
    @Query("select u from AppUser u where u.role = com.carex.leave.org.Role.HR and u.active = true " +
           "and exists (select t from Team t where t.managerId = u.id) order by u.id")
    List<AppUser> findHrHeads();
}
