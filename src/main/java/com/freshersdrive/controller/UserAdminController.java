package com.freshersdrive.controller;

import com.freshersdrive.entity.User;
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