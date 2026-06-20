package com.freshersdrive.config;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.ReviewStatus;
import com.freshersdrive.repository.DriveRepository;
import com.freshersdrive.service.DriveFingerprintGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs once on every startup. Backfills `fingerprint` and `reviewStatus`
 * on any existing Drive rows that don't have them yet (i.e. rows created
 * before this feature existed). Safe to leave in permanently — it's a
 * no-op once every row has a fingerprint, since the check is per-row.
 *
 * Run this BEFORE adding a unique DB constraint on fingerprint, or the
 * constraint migration will fail against existing null values.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DriveBackfillRunner implements ApplicationRunner {

    private final DriveRepository driveRepository;
    private final DriveFingerprintGenerator fingerprintGenerator;

    @Override
    public void run(ApplicationArguments args) {
        List<Drive> drivesMissingFingerprint = driveRepository.findAll().stream()
            .filter(d -> d.getFingerprint() == null || d.getFingerprint().isBlank())
            .toList();

        if (drivesMissingFingerprint.isEmpty()) {
            return;
        }

        log.info("Backfilling fingerprint for {} existing drive(s)...", drivesMissingFingerprint.size());

        for (Drive drive : drivesMissingFingerprint) {
            String fingerprint = fingerprintGenerator.generate(
                drive.getCompanyName(), drive.getJobRole(), drive.getLocation()
            );
            drive.setFingerprint(fingerprint);

            if (drive.getReviewStatus() == null) {
                drive.setReviewStatus(ReviewStatus.APPROVED); // pre-existing manual drives
            }
            if (drive.getDeadlineGuessed() == null) {
                drive.setDeadlineGuessed(false);
            }
        }

        driveRepository.saveAll(drivesMissingFingerprint);
        log.info("Backfill complete.");
    }
} 