package com.freshersdrive.controller;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.service.DriveReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/drives")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class DriveReviewController {

    private final DriveReviewService driveReviewService;

    // ── Pending drives ─────────────────────────────────────────────────────
    @GetMapping("/pending")
    public ResponseEntity<List<Drive>> getPending() {
        return ResponseEntity.ok(driveReviewService.getPendingDrives());
    }

    // ── Rejected drives ────────────────────────────────────────────────────
    @GetMapping("/rejected")
    public ResponseEntity<List<Drive>> getRejected() {
        return ResponseEntity.ok(driveReviewService.getRejectedDrives());
    }

    // ── Last 5 approved (reference panel) ─────────────────────────────────
    @GetMapping("/last-approved")
    public ResponseEntity<List<Drive>> getLastApproved() {
        return ResponseEntity.ok(driveReviewService.getLast5Approved());
    }

    // ── Rejection cooldown status for current user ─────────────────────────
    @GetMapping("/rejection-status")
    public ResponseEntity<Map<String, Object>> getRejectionStatus(Authentication auth) {
        return ResponseEntity.ok(driveReviewService.getRejectionStatus(auth.getName()));
    }

    // ── Approve ────────────────────────────────────────────────────────────
    @PostMapping("/{id}/approve")
    public ResponseEntity<Drive> approve(@PathVariable Long id) {
        return ResponseEntity.ok(driveReviewService.approve(id));
    }

    // ── Reject (rate-limited for employees) ───────────────────────────────
    @PostMapping("/{id}/reject")
    public ResponseEntity<?> reject(@PathVariable Long id, Authentication auth) {
        try {
            boolean isAdmin = auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
            return ResponseEntity.ok(driveReviewService.reject(id, isAdmin));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(429)
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Edit drive fields before approving ────────────────────────────────
    @PatchMapping("/{id}/edit")
    public ResponseEntity<Drive> edit(
            @PathVariable Long id,
            @RequestBody Map<String, String> fields) {
        return ResponseEntity.ok(driveReviewService.edit(id, fields));
    }
}