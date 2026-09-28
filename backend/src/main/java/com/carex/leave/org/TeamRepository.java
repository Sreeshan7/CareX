package com.carex.leave.org;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByManagerId(Long managerId);

    Optional<Team> findByName(String name);
}
