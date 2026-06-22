package com.freshersdrive.service;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.ReviewStatus;
import com.freshersdrive.repository.DriveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DriveReviewService {

    private final DriveRepository driveRepository;

    // In-memory rate limit tracker: email -> list of rejection timestamps
    private final Map<String, List<LocalDateTime>> rejectionTracker = new ConcurrentHashMap<>();

    private static final int  MAX_REJECTIONS_PER_HOUR = 5;
    private static final long COOLDOWN_HOURS          = 1;

    // ── Fetch pending ──────────────────────────────────────────────────────
    public List<Drive> getPendingDrives() {
        return driveRepository.findByReviewStatus(ReviewStatus.PENDING_REVIEW);
    }

    // ── Fetch rejected ─────────────────────────────────────────────────────
    public List<Drive> getRejectedDrives() {
        return driveRepository.findByReviewStatus(ReviewStatus.REJECTED);
    }

    // ── Last 5 approved for reference panel ───────────────────────────────
    public List<Drive> getLast5Approved() {
        return driveRepository.findTop5ByReviewStatusOrderByUpdatedAtDesc(ReviewStatus.APPROVED);
    }

    // ── Approve ────────────────────────────────────────────────────────────
    public Drive approve(Long driveId) {
        Drive drive = getOrThrow(driveId);
        drive.setReviewStatus(ReviewStatus.APPROVED);
        drive.setStatus(DriveStatus.UPCOMING);
        return driveRepository.save(drive);
    }

    // ── Reject (with rate limiting for non-admins) ─────────────────────────
    public Drive reject(Long driveId, boolean isAdmin) {
        if (!isAdmin) {
            checkRateLimit();
        }
        Drive drive = getOrThrow(driveId);
        drive.setReviewStatus(ReviewStatus.REJECTED);
        return driveRepository.save(drive);
    }

    // ── Edit drive fields before approving ────────────────────────────────
    public Drive edit(Long driveId, Map<String, String> fields) {
        Drive drive = getOrThrow(driveId);

        if (fields.containsKey("companyName") && fields.get("companyName") != null)
            drive.setCompanyName(fields.get("companyName"));

        if (fields.containsKey("jobRole") && fields.get("jobRole") != null)
            drive.setJobRole(fields.get("jobRole"));

        if (fields.containsKey("location") && fields.get("location") != null)
            drive.setLocation(fields.get("location"));

        if (fields.containsKey("ctcDisplay") && fields.get("ctcDisplay") != null)
            drive.setCtcDisplay(fields.get("ctcDisplay"));

        if (fields.containsKey("eligibleBatches") && fields.get("eligibleBatches") != null)
            drive.setEligibleBatches(fields.get("eligibleBatches"));

        if (fields.containsKey("applyLink") && fields.get("applyLink") != null)
            drive.setApplyLink(fields.get("applyLink"));

        if (fields.containsKey("deadline") && fields.get("deadline") != null) {
            try {
                drive.setDeadline(LocalDate.parse(fields.get("deadline")));
                drive.setDeadlineGuessed(false); // manually set = no longer guessed
            } catch (Exception ignored) {}
        }

        return driveRepository.save(drive);
    }

    // ── Rate limit check ───────────────────────────────────────────────────
    private void checkRateLimit() {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(COOLDOWN_HOURS);

        List<LocalDateTime> timestamps = rejectionTracker
            .getOrDefault(email, new ArrayList<>());

        // Remove timestamps older than 1 hour
        timestamps = timestamps.stream()
            .filter(t -> t.isAfter(oneHourAgo))
            .collect(Collectors.toList());

        if (timestamps.size() >= MAX_REJECTIONS_PER_HOUR) {
            LocalDateTime earliest    = timestamps.get(0);
            LocalDateTime cooldownEnd = earliest.plusHours(COOLDOWN_HOURS);
            long minutesLeft = Duration.between(LocalDateTime.now(), cooldownEnd).toMinutes() + 1;
            throw new IllegalStateException(
                "Rejection limit reached. Try again in " + minutesLeft + " minute(s).");
        }

        timestamps.add(LocalDateTime.now());
        rejectionTracker.put(email, timestamps);
    }

    // ── Auto-delete rejected drives older than 3 days ─────────────────────
    public void deleteOldRejected() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(3);
        List<Drive> old = driveRepository
            .findByReviewStatusAndUpdatedAtBefore(ReviewStatus.REJECTED, cutoff);
        if (!old.isEmpty()) {
            driveRepository.deleteAll(old);
            System.out.println("Auto-deleted " + old.size() + " old rejected drive(s).");
        }
    }

    // ── Get rejection cooldown info for a user ─────────────────────────────
    public Map<String, Object> getRejectionStatus(String email) {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(COOLDOWN_HOURS);

        List<LocalDateTime> timestamps = rejectionTracker
            .getOrDefault(email, new ArrayList<>()).stream()
            .filter(t -> t.isAfter(oneHourAgo))
            .collect(Collectors.toList());

        int used = timestamps.size();
        int remaining = MAX_REJECTIONS_PER_HOUR - used;
        boolean onCooldown = used >= MAX_REJECTIONS_PER_HOUR;

        long cooldownMinutesLeft = 0;
        if (onCooldown && !timestamps.isEmpty()) {
            LocalDateTime cooldownEnd = timestamps.get(0).plusHours(COOLDOWN_HOURS);
            cooldownMinutesLeft = Duration.between(LocalDateTime.now(), cooldownEnd).toMinutes() + 1;
        }

        return Map.of(
            "used", used,
            "remaining", Math.max(0, remaining),
            "onCooldown", onCooldown,
            "cooldownMinutesLeft", cooldownMinutesLeft
        );
    }

    private Drive getOrThrow(Long driveId) {
        return driveRepository.findById(driveId)
            .orElseThrow(() -> new NoSuchElementException("Drive not found: " + driveId));
    }
}