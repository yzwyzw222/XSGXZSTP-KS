package com.aacv.domain.user;

import com.aacv.domain.common.BaseEntity;
import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Set;

@Entity
@Table(name = "users")
public class User extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String displayName;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    private Set<Role> roles;

    @Column(nullable = false)
    private boolean enabled = true;

    protected User() {
    }

    public User(String username, String password, String displayName, Set<Role> roles) {
        this.username = username;
        this.password = password;
        this.displayName = displayName;
        this.roles = roles;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Set<Role> getRoles() {
        return roles;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}