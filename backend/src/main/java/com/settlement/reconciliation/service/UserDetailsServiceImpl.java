package com.settlement.reconciliation.service;

import com.settlement.reconciliation.model.User;
import com.settlement.reconciliation.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        boolean enabled = user.getEnabled() == null || user.getEnabled();
        boolean locked = user.getLocked() != null && user.getLocked();
        String role = normalizeRole(user.getRole());

        return org.springframework.security.core.userdetails.User
            .withUsername(user.getUsername())
            .password(user.getPassword())
            .disabled(!enabled)
            .accountLocked(locked)
            .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + role)))
            .build();
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            return "USER";
        }
        String normalized = role.trim().toUpperCase(Locale.ROOT);
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring("ROLE_".length());
        }
        if (!"ADMIN".equals(normalized) && !"USER".equals(normalized)) {
            return "USER";
        }
        return normalized;
    }
}
