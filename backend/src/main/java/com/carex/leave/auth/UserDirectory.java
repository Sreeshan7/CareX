package com.carex.leave.auth;

import com.carex.leave.org.AppUser;
import com.carex.leave.org.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads the principal for each request. Role/active flag are re-read from the DB with a 60s cache,
 * so a demoted or deactivated user's JWT loses privileges within a minute (implementation.md §13).
 */
@Component
public class UserDirectory {
    private static final long TTL_NANOS = Duration.ofSeconds(60).toNanos();
    private final UserRepository users;
    private final ConcurrentHashMap<Long, Entry> cache = new ConcurrentHashMap<>();

    public UserDirectory(UserRepository users) {
        this.users = users;
    }

    public Optional<CurrentUser> load(Long id) {
        long now = System.nanoTime();
        Entry e = cache.get(id);
        if (e != null && now - e.loadedAt < TTL_NANOS) {
            return Optional.ofNullable(e.user);
        }
        CurrentUser u = users.findById(id).filter(AppUser::isActive).map(CurrentUser::of).orElse(null);
        cache.put(id, new Entry(u, now));
        return Optional.ofNullable(u);
    }

    public void evictAll() {
        cache.clear();
    }

    private record Entry(CurrentUser user, long loadedAt) {}
}
