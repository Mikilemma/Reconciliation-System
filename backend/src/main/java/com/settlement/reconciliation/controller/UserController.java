package com.settlement.reconciliation.controller;

import com.settlement.reconciliation.model.User;
import com.settlement.reconciliation.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/p2p/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ResponseEntity<List<User>> listUsers() {
        return ResponseEntity.ok(userRepository.findAll());
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody Map<String, String> payload, Authentication authentication) {
        String username = payload.get("username");
        String password = payload.get("password");
        if (username == null || username.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        boolean hasExistingUsers = userRepository.count() > 0;
        boolean isAdmin = isAdmin(authentication);

        // Allow unauthenticated bootstrap only for the first system user.
        if (hasExistingUsers && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Optional<User> existing = userRepository.findByUsername(username.trim());
        if (existing.isPresent()) {
            return ResponseEntity.ok(existing.get());
        }

        User user = new User();
        user.setUsername(username.trim());
        String raw = (password == null || password.trim().isEmpty()) ? "change_me" : password.trim();
        user.setPassword(passwordEncoder.encode(raw));
        user.setEnabled(true);
        user.setLocked(false);

        String requestedRole = payload.get("role");
        if (!hasExistingUsers) {
            user.setRole("ADMIN");
        } else {
            user.setRole(normalizeRole(requestedRole));
        }
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<User> resetPassword(@PathVariable String id, @RequestBody Map<String, String> payload) {
        String newPassword = payload.get("newPassword");
        if (newPassword == null || newPassword.trim().isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = optUser.get();
        user.setPassword(passwordEncoder.encode(newPassword.trim()));
        return ResponseEntity.ok(userRepository.save(user));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<User> updateStatus(@PathVariable String id, @RequestBody Map<String, Object> payload) {
        Optional<User> optUser = userRepository.findById(id);
        if (optUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        User user = optUser.get();
        if (payload.containsKey("enabled")) {
            Object enabled = payload.get("enabled");
            if (enabled instanceof Boolean) {
                user.setEnabled((Boolean) enabled);
            }
        }
        if (payload.containsKey("locked")) {
            Object locked = payload.get("locked");
            if (locked instanceof Boolean) {
                user.setLocked((Boolean) locked);
            }
        }
        return ResponseEntity.ok(userRepository.save(user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        if (!userRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        userRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null) return false;
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch("ROLE_ADMIN"::equals);
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
