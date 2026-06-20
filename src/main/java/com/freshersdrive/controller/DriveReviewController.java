package com.freshersdrive.controller;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.service.DriveReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only endpoints for reviewing AI-discovered drives before they go
 * live. Wire these into AdminDashboard.jsx's pending-drives section.
 *
 * Adjust @PreAuthorize to match however your existing admin endpoints
 * guard access (this assumes the same ROLE_ADMIN convention used by
 * ProtectedRoute on the frontend).
 */
@RestController
@RequestMapping("/api/admin/drives/pending")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DriveReviewController {

    private final DriveReviewService driveReviewService;

    @GetMapping
    public ResponseEntity<List<Drive>> getPending() {
        return ResponseEntity.ok(driveReviewService.getPendingDrives());
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Drive> approve(@PathVariable Long id) {
        return ResponseEntity.ok(driveReviewService.approve(id));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Drive> reject(@PathVariable Long id) {
        return ResponseEntity.ok(driveReviewService.reject(id));
    }
}