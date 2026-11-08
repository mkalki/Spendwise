package com.spendwise.auth.service;

import com.spendwise.auth.entity.UserAccount;
import java.security.Principal;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;

public class AccountPrincipal implements UserDetails, OAuth2User, Principal {

    private final UserAccount account;

    public AccountPrincipal(UserAccount account) {
        this.account = account;
    }

    @Override
    public String getName() {
        return account.getId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return java.util.List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public Map<String, Object> getAttributes() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sub", account.getId());
        attributes.put("email", account.getEmail());
        attributes.put("name", account.getDisplayName());
        attributes.put("picture", account.getPictureUrl());
        attributes.values().removeIf(value -> value == null);
        return Map.copyOf(attributes);
    }

    @Override
    public String getPassword() {
        return account.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return account.getId();
    }

    public String getEmail() { return account.getEmail(); }
    public String getDisplayName() { return account.getDisplayName(); }
    public String getPictureUrl() { return account.getPictureUrl(); }
    public String getProvider() { return account.getProvider().name(); }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
