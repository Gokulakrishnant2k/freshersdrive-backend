package com.freshersdrive.scheduler;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.repository.DriveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DriveCleanupScheduler {

    private final DriveRepository driveRepository;

    @Scheduled(cron = "0 0 2 * * *") // daily 2 AM
    public void deleteExpiredDrives() {

        LocalDate cutoff = LocalDate.now().minusDays(30);

        List<Drive> expired = driveRepository.findByDeadlineBefore(cutoff);

        // ONLY delete if auto-delete is ON
        List<Drive> toDelete = expired.stream()
                .filter(d -> Boolean.TRUE.equals(d.getAutoDeleteEnabled()))
                .toList();

        driveRepository.deleteAll(toDelete);

        System.out.println("Auto-deleted drives: " + toDelete.size());
    }
}