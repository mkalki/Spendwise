package com.spendwise.auth.service;

import com.spendwise.auth.entity.UserAccount;
import com.spendwise.auth.repository.UserAccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final UserAccountRepository repository;

    public AccountUserDetailsService(UserAccountRepository repository) {
        this.repository = repository;
    }

    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        String normalized = identifier.strip().toLowerCase(java.util.Locale.ROOT);
        UserAccount account = repository
                .findByUsernameIgnoreCaseOrEmailIgnoreCase(normalized, normalized)
                .filter(candidate -> candidate.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        return new AccountPrincipal(account);
    }
}
