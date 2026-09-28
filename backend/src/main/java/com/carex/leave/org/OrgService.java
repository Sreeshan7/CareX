package com.carex.leave.org;

import com.carex.leave.common.error.Errors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reporting-line rules (assumption A2): team.manager_id approves members of that team at the manager stage.
 * A manager is a member of a different team whose manager is their skip-level.
 */
@Service
@Transactional(readOnly = true)
public class OrgService {
    private final UserRepository users;
    private final TeamRepository teams;

    public OrgService(UserRepository users, TeamRepository teams) {
        this.users = users;
        this.teams = teams;
    }

    /** Who approves {@code user}'s requests at the manager stage. */
    public ManagerRouting managerApproverFor(AppUser user) {
        Team team = teams.findById(user.getTeamId()).orElseThrow();
        Long mgr = team.getManagerId();
        if (mgr != null && !mgr.equals(user.getId()) && users.findById(mgr).map(AppUser::isActive).orElse(false)) {
            return new ManagerRouting(mgr, false);
        }
        // Top of the hierarchy (or no manager): the HR head is the manager-stage proxy so two humans still decide.
        AppUser head = hrHead().filter(h -> !h.getId().equals(user.getId()))
                .or(() -> users.findByRoleAndActiveTrueOrderById(Role.HR).stream()
                        .filter(h -> !h.getId().equals(user.getId())).findFirst())
                .orElseThrow(() -> Errors.businessRule("NO_APPROVER", "No manager-stage approver is configured for you"));
        return new ManagerRouting(head.getId(), true);
    }

    /** Skip-level of a manager: the manager of the team the manager belongs to. Empty if none/invalid. */
    public Optional<Long> skipLevelOf(Long managerId, Long ownerId) {
        return users.findById(managerId)
                .flatMap(m -> teams.findById(m.getTeamId()))
                .map(Team::getManagerId)
                .filter(sl -> !sl.equals(managerId) && !sl.equals(ownerId))
                .filter(sl -> users.findById(sl).map(AppUser::isActive).orElse(false));
    }

    public Optional<AppUser> hrHead() {
        return users.findHrHeads().stream().findFirst();
    }

    public List<AppUser> hrUsers() {
        return users.findByRoleAndActiveTrueOrderById(Role.HR);
    }

    public List<Long> teamIdsManagedBy(Long userId) {
        return teams.findByManagerId(userId).stream().map(Team::getId).toList();
    }

    public List<AppUser> activeMembers(Long teamId) {
        return users.findByTeamIdAndActiveTrueOrderByFullName(teamId);
    }

    public AppUser user(Long id) {
        return users.findById(id).orElseThrow(() -> Errors.notFound("User not found"));
    }

    public Team team(Long id) {
        return teams.findById(id).orElseThrow(() -> Errors.notFound("Team not found"));
    }

    public Map<Long, AppUser> usersById(Collection<Long> ids) {
        return users.findAllById(ids).stream().collect(Collectors.toMap(AppUser::getId, Function.identity()));
    }

    public Map<Long, Team> allTeams() {
        return teams.findAll().stream().collect(Collectors.toMap(Team::getId, Function.identity()));
    }

    public record ManagerRouting(Long approverId, boolean routedToHr) {}
}
