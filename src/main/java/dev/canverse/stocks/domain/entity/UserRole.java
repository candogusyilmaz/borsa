package dev.canverse.stocks.domain.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

@Getter
@Entity
@Table(name = "user_roles")
public class UserRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private User user;

    @NotNull
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Role role;

    protected UserRole() {
    }

    public UserRole(User user, Role role) {
        this.user = user;
        this.role = role;
    }
}