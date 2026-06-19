package com.freshersdrive.controller;

import com.freshersdrive.entity.User;
import com.freshersdrive.enums.Role;
import com.freshersdrive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin/users")
@RequiredArgsConstructor
@Slf4j
public class UserAdminController {

    private final UserRepository userRepository;

    // ── GET /admin/users ─────────────────────────────────────────────────────
    // Returns a lightweight list — never expose password hashes or tokens.

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listUsers() {
        List<Map<String, Object>> users = userRepository.findAll().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    // ── PUT /admin/users/{id}/role ───────────────────────────────────────────
    // Promote a user to Employee, or demote an Employee back to User.
    // Deliberately cannot be used to grant or revoke ADMIN — that must be
    // done directly in the database, never over this endpoint.

    @PutMapping("/{id}/role")
    public ResponseEntity<Map<String, Object>> updateRole(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        User target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "User not found."));
        }

        // Guard: never touch an existing admin's role through this endpoint
        if (target.getRole() == Role.ROLE_ADMIN) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Admin roles cannot be changed from here."));
        }

        String requestedRole = body.get("role");
        Role newRole;
        try {
            newRole = Role.valueOf(requestedRole);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid role value."));
        }

        // Guard: this endpoint only toggles USER <-> EMPLOYEE, never grants ADMIN
        if (newRole != Role.ROLE_USER && newRole != Role.ROLE_EMPLOYEE) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Role must be ROLE_USER or ROLE_EMPLOYEE."));
        }

        target.setRole(newRole);
        userRepository.save(target);

        String currentAdminEmail = authentication.getName();
        log.info("Admin '{}' set role of '{}' (id={}) to {}", currentAdminEmail, target.getEmail(), id, newRole);

        return ResponseEntity.ok(Map.of("id", target.getId(), "role", target.getRole().name()));
    }

    // ── DELETE /admin/users/{id} ─────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> deleteUser(
            @PathVariable Long id,
            Authentication authentication) {

        User target = userRepository.findById(id).orElse(null);
        if (target == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "User not found."));
        }

        // Guard: don't let an admin delete their own account via this endpoint
        String currentAdminEmail = authentication.getName();
        if (target.getEmail().equalsIgnoreCase(currentAdminEmail)) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "You cannot delete your own account from here."));
        }

        // Guard: admin accounts can't be deleted through this endpoint, period —
        // not just self. The frontend hides this button for admin rows, but
        // that's UI-only and shouldn't be the only line of defense.
        if (target.getRole() == Role.ROLE_ADMIN) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Admin accounts cannot be deleted from here."));
        }

        userRepository.delete(target);
        log.info("Admin '{}' deleted user '{}' (id={})", currentAdminEmail, target.getEmail(), id);

        return ResponseEntity.ok(Map.of("message", "User deleted successfully."));
    }

    // ── Helper: strip sensitive fields before returning to the client ─────────

    private Map<String, Object> toSummary(User u) {
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        m.put("role", u.getRole() != null ? u.getRole().name() : null);
        m.put("college", u.getCollege());
        m.put("branch", u.getBranch());
        m.put("batchYear", u.getBatchYear());
        m.put("emailVerified", u.isEmailVerified());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }
}