package dev.canverse.stocks.repository;

import dev.canverse.stocks.domain.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends BaseJpaRepository<User, Long> {
    @Query("select u from User u where u.username = :username")
    @EntityGraph(attributePaths = {"userRoles.role.rolePermissions.permission"})
    Optional<User> findByUsernameIncludePermissions(String username);

    @Query("select u from User u where u.email = :email")
    @EntityGraph(attributePaths = {"userRoles.role.rolePermissions.permission"})
    Optional<User> findByEmailIncludePermissions(String email);

    Optional<User> findByUsername(String username);
}