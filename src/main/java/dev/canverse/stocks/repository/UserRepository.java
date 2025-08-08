package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.account.User;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends BaseJpaRepository<User, Long> {
    @Cacheable(value = "userCacheByEmail", key = "#email", unless = "#result == null")
    @Query("select u from User u where lower(u.email) = lower(:email)")
    @EntityGraph(attributePaths = {"userRoles.role.rolePermissions.permission"})
    Optional<User> findByEmailIncludePermissions(String email);

    boolean existsByEmailIgnoreCase(String email);
}