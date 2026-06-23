package com.freshersdrive.scheduler;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.repository.DriveRepository;
import com.freshersdrive.service.DriveReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DriveCleanupScheduler {

    private final DriveRepository    driveRepository;
    private final DriveReviewService driveReviewService;

    // ── Delete drives where autoExpireAfter30Days=true and deadline > 30 days ago ──
    @Scheduled(cron = "0 0 2 * * *") // daily 2 AM
    public void deleteExpiredDrives() {
        LocalDate cutoff = LocalDate.now().minusDays(30);

        List<Drive> expired = driveRepository.findByDeadlineBefore(cutoff);

        List<Drive> toDelete = expired.stream()
                .filter(d -> Boolean.TRUE.equals(d.getAutoExpireAfter30Days()))
                .toList();

        driveRepository.deleteAll(toDelete);
        System.out.println("Auto-deleted expired drives: " + toDelete.size());
    }

    // ── Delete rejected AI-discovered drives older than 3 days ────────────
    @Scheduled(cron = "0 0 3 * * *") // daily 3 AM
    public void deleteOldRejectedDrives() {
        driveReviewService.deleteOldRejected();
    }
}