package com.freshersdrive.controller;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.ReviewStatus;
import com.freshersdrive.repository.DriveRepository;
import com.freshersdrive.service.DriveReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/drives")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
public class DriveReviewController {

    private final DriveReviewService driveReviewService;
    private final DriveRepository    driveRepository;

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

    // ── Restore: move a rejected drive back to PENDING_REVIEW ─────────────
    @PostMapping("/{id}/restore")
    public ResponseEntity<Drive> restore(@PathVariable Long id) {
        return driveRepository.findById(id)
                .map(drive -> {
                    drive.setReviewStatus(ReviewStatus.PENDING_REVIEW);
                    return ResponseEntity.ok(driveRepository.save(drive));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── Edit drive fields before approving ────────────────────────────────
    @PatchMapping("/{id}/edit")
    public ResponseEntity<Drive> edit(
            @PathVariable Long id,
            @RequestBody Map<String, String> fields) {
        return ResponseEntity.ok(driveReviewService.edit(id, fields));
    }

    // ── Edit via PUT (full form from AdminDiscoveryTab) ───────────────────
    // Accepts the same shape as the edit modal form so the frontend
    // PUT /admin/drives/{id} works without a separate DTO class.
    @PutMapping("/{id}")
    public ResponseEntity<Drive> editFull(
            @PathVariable Long id,
            @RequestBody Map<String, Object> fields) {
        return driveRepository.findById(id)
                .map(drive -> {
                    if (fields.containsKey("companyName"))
                        drive.setCompanyName((String) fields.get("companyName"));
                    if (fields.containsKey("jobRole"))
                        drive.setJobRole((String) fields.get("jobRole"));
                    if (fields.containsKey("jobDescription"))
                        drive.setJobDescription((String) fields.get("jobDescription"));
                    if (fields.containsKey("keySkills"))
                        drive.setKeySkills((String) fields.get("keySkills"));
                    if (fields.containsKey("location"))
                        drive.setLocation((String) fields.get("location"));
                    if (fields.containsKey("ctcDisplay"))
                        drive.setCtcDisplay((String) fields.get("ctcDisplay"));
                    if (fields.containsKey("minCgpa") && fields.get("minCgpa") != null) {
                        try { drive.setMinCgpa(Double.parseDouble(fields.get("minCgpa").toString())); }
                        catch (NumberFormatException ignored) {}
                    }
                    if (fields.containsKey("deadline") && fields.get("deadline") != null
                            && !fields.get("deadline").toString().isBlank())
                        drive.setDeadline(LocalDate.parse(fields.get("deadline").toString()));
                    if (fields.containsKey("eligibleBranches"))
                        drive.setEligibleBranches((String) fields.get("eligibleBranches"));
                    if (fields.containsKey("eligibleBatches"))
                        drive.setEligibleBatches((String) fields.get("eligibleBatches"));
                    if (fields.containsKey("experienceLevel"))
                        drive.setExperienceLevel((String) fields.get("experienceLevel"));
                    if (fields.containsKey("applyLink"))
                        drive.setApplyLink((String) fields.get("applyLink"));
                    if (fields.containsKey("jobType"))
                        drive.setJobType((String) fields.get("jobType"));
                    if (fields.containsKey("autoDeleteEnabled") && fields.get("autoDeleteEnabled") != null)
                        drive.setAutoDeleteEnabled(Boolean.parseBoolean(fields.get("autoDeleteEnabled").toString()));
                    return ResponseEntity.ok(driveRepository.save(drive));
                })
                .orElse(ResponseEntity.notFound().build());
    }
}