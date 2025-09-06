package dev.canverse.stocks.domain.entity.account;

import dev.canverse.stocks.domain.entity.portfolio.Dashboard;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Entity
@Table(schema = "account", name = "users")
public class User implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotEmpty
    @Column(nullable = false)
    private String name;

    @NotEmpty
    @Column(nullable = false)
    private String password;

    @Email
    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private Boolean isEnabled;

    @Column(nullable = false)
    private Instant lastLoginAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;


    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRole> userRoles = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Dashboard> dashboards = new HashSet<>();

    protected User() {
    }

    public User(String email, String password) {
        this.email = email;
        this.password = password;
        this.isEnabled = true;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // get userRoles -> get roles -> get rolePermissions -> get permissions -> get permission names
        return this.getUserRoles().stream()
                .flatMap(ur -> ur.getRole().getRolePermissions().stream())
                .map(RolePermission::getPermission)
                .toList();
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    public void addDashboard(Dashboard dashboard) {
        if (this.dashboards.contains(dashboard)) {
            return;
        }

        if (!dashboard.getUser().getId().equals(this.id)) {
            throw new IllegalArgumentException("Dashboard does not belong to the same user.");
        }

        this.dashboards.add(dashboard);
    }
}
