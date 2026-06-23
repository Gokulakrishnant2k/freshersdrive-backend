package com.freshersdrive.controller;

import com.freshersdrive.dto.DriveDto;
import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.ReviewStatus;
import com.freshersdrive.repository.DriveRepository;
import com.freshersdrive.service.DriveNotificationService;
import com.freshersdrive.service.DriveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/drives")
@RequiredArgsConstructor
public class DriveController {

    private final DriveRepository           driveRepository;
    private final DriveService              driveService;
    private final DriveNotificationService  driveNotificationService;

    // ── GET ALL — only APPROVED drives for public ────────────────────────────
    @GetMapping
    public ResponseEntity<List<Drive>> getAllDrives() {
        return ResponseEntity.ok(
            driveRepository.findByReviewStatus(ReviewStatus.APPROVED)
        );
    }

    // ── GET BY ID ────────────────────────────────────────────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<Drive> getDriveById(@PathVariable Long id) {
        return driveRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── FEATURED DRIVES — only APPROVED ──────────────────────────────────────
    @GetMapping("/featured")
    public ResponseEntity<List<Drive>> getFeaturedDrives() {
        return ResponseEntity.ok(
            driveRepository.findTop6ByStatusAndIsFeaturedTrueOrderByDeadlineAsc(DriveStatus.ACTIVE)
        );
    }

    // ── RECOMMENDED — only APPROVED drives ───────────────────────────────────
    @GetMapping("/recommended")
    public ResponseEntity<List<Drive>> getRecommendedDrives(
            @RequestParam(required = false) String branch,
            @RequestParam(required = false) String batch) {

        List<Drive> recommended = driveRepository.findByReviewStatus(ReviewStatus.APPROVED)
                .stream()
                .filter(drive -> {
                    boolean branchMatch = true;
                    if (branch != null && !branch.isBlank()
                            && drive.getEligibleBranches() != null
                            && !drive.getEligibleBranches().isBlank()) {
                        branchMatch = containsIgnoreCase(drive.getEligibleBranches(), branch);
                    }
                    boolean batchMatch = true;
                    if (batch != null && !batch.isBlank()
                            && drive.getEligibleBatches() != null
                            && !drive.getEligibleBatches().isBlank()) {
                        batchMatch = containsIgnoreCase(drive.getEligibleBatches(), batch);
                    }
                    return branchMatch && batchMatch;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(recommended);
    }

    // ── CREATE ───────────────────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Drive> createDrive(@Valid @RequestBody DriveDto.Request req) {
        Drive drive = mapToDrive(req, new Drive());
        Drive saved = driveService.createDrive(drive);
        return ResponseEntity.ok(saved);
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'EMPLOYEE')")
    public ResponseEntity<Drive> updateDrive(
            @PathVariable Long id,
            @Valid @RequestBody DriveDto.Request req) {

        return driveRepository.findById(id)
                .map(existing -> {
                    boolean isCancelling =
                        req.getStatus() == DriveStatus.CANCELLED &&
                        existing.getStatus() != DriveStatus.CANCELLED;

                    Drive updated = mapToDrive(req, existing);
                    Drive saved  = driveRepository.save(updated);

                    if (isCancelling) {
                        driveNotificationService.notifyRegisteredStudentsCancellation(saved);
                    }
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE ───────────────────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> deleteDrive(@PathVariable Long id) {
        if (!driveRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        driveRepository.deleteById(id);
        return ResponseEntity.ok("Drive deleted successfully");
    }

    // ── TOGGLE FEATURED (highlight) ──────────────────────────────────────────
    @PatchMapping("/{id}/featured")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> toggleFeatured(@PathVariable Long id) {
        return driveRepository.findById(id)
                .map(drive -> {
                    boolean nowFeatured = !(Boolean.TRUE.equals(drive.getIsFeatured()));
                    drive.setIsFeatured(nowFeatured);
                    driveRepository.save(drive);
                    return ResponseEntity.ok(Map.<String, Object>of(
                        "id", id,
                        "isFeatured", nowFeatured
                    ));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ── TOGGLE AUTO-DELETE ───────────────────────────────────────────────────
    @PatchMapping("/{id}/auto-delete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Drive> toggleAutoDelete(@PathVariable Long id) {
        return driveRepository.findById(id)
                .map(drive -> {
                    Boolean current = drive.getAutoDeleteEnabled();
                    drive.setAutoDeleteEnabled(current == null || !current);
                    return ResponseEntity.ok(driveRepository.save(drive));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Calendar Endpoints
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/calendar")
    public ResponseEntity<List<Drive>> getDrivesByMonth(
            @RequestParam int year,
            @RequestParam int month) {
        return ResponseEntity.ok(driveService.getDrivesByMonth(year, month));
    }

    @GetMapping("/upcoming")
    public ResponseEntity<List<Drive>> getUpcomingDrives() {
        return ResponseEntity.ok(driveService.getUpcomingDrives());
    }

    @GetMapping("/search")
    public ResponseEntity<List<Drive>> searchDrives(@RequestParam String q) {
        return ResponseEntity.ok(driveService.searchDrives(q));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Notification Preference Endpoints
    // ════════════════════════════════════════════════════════════════════════

    @GetMapping("/notify-preference")
    public ResponseEntity<Map<String, Boolean>> getNotifyPreference(
            @AuthenticationPrincipal UserDetails userDetails) {
        boolean enabled = driveNotificationService.getNotifyPreference(userDetails.getUsername());
        return ResponseEntity.ok(Map.of("enabled", enabled));
    }

    @PostMapping("/notify-preference")
    public ResponseEntity<Void> setNotifyPreference(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody Map<String, Boolean> body) {
        driveNotificationService.setNotifyPreference(userDetails.getUsername(), body.get("enabled"));
        return ResponseEntity.ok().build();
    }

    // ── PRIVATE HELPERS ──────────────────────────────────────────────────────

    private boolean containsIgnoreCase(String listString, String target) {
        for (String token : listString.split("[,;]+")) {
            if (token.trim().equalsIgnoreCase(target.trim())) return true;
        }
        return false;
    }

    private Drive mapToDrive(DriveDto.Request req, Drive drive) {
        drive.setCompanyName(req.getCompanyName());
        drive.setCompanyLogoUrl(req.getCompanyLogoUrl());
        drive.setCompanyDescription(req.getCompanyDescription());
        drive.setCompanyWebsite(req.getCompanyWebsite());

        drive.setJobRole(req.getJobRole());
        drive.setJobDescription(req.getJobDescription());

        drive.setCategory(req.getCategory());
        drive.setJobType(req.getJobType());
        drive.setLocation(req.getLocation());
        drive.setIsRemote(req.getIsRemote() != null ? req.getIsRemote() : false);

        drive.setCtcMin(req.getCtcMin());
        drive.setCtcMax(req.getCtcMax());
        drive.setCtcDisplay(req.getCtcDisplay());
        drive.setStipend(req.getStipend());

        drive.setEligibleDegrees(req.getEligibleDegrees());
        drive.setEligibleBranches(req.getEligibleBranches());
        drive.setMinCgpa(req.getMinCgpa());
        drive.setEligibleBatches(req.getEligibleBatches());
        drive.setExperienceLevel(
                req.getExperienceLevel() != null ? req.getExperienceLevel() : "Freshers");
        drive.setMaxBacklogs(req.getMaxBacklogs());
        drive.setOtherEligibilityCriteria(req.getOtherEligibilityCriteria());

        drive.setSelectionProcess(req.getSelectionProcess());
        drive.setSelectionDetails(req.getSelectionDetails());

        drive.setDriveDate(req.getDriveDate() != null ? req.getDriveDate() : req.getDeadline());
        drive.setDeadline(req.getDeadline());
        drive.setApplyLink(req.getApplyLink());

        drive.setStatus(req.getStatus());
        drive.setIsFeatured(req.getIsFeatured() != null ? req.getIsFeatured() : false);
        drive.setAutoDeleteEnabled(
                req.getAutoDeleteEnabled() != null ? req.getAutoDeleteEnabled() : false);

        if (drive.getViewCount() == null) drive.setViewCount(0);

        return drive;
    }
}