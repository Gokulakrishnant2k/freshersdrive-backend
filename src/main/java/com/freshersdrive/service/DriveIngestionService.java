package com.freshersdrive.service;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveSource;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.ReviewStatus;
import com.freshersdrive.repository.DriveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Single entry point both discovery paths (search-based and url-context-based)
 * funnel through, so dedup is consistent no matter which one found a posting.
 *
 * IMPORTANT: this does NOT touch DriveStatus or make a drive visible anywhere.
 * Every row created here sits at ReviewStatus.PENDING_REVIEW until an admin
 * approves it (see DriveReviewService), at which point a real DriveStatus
 * gets assigned and it becomes visible like any manually-added drive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriveIngestionService {

    private static final int FALLBACK_DEADLINE_DAYS_OUT = 30;

    private final DriveRepository driveRepository;
    private final DriveFingerprintGenerator fingerprintGenerator;

    public void ingest(List<ScrapedDriveDTO> scrapedDrives, DriveSource source) {
        for (ScrapedDriveDTO dto : scrapedDrives) {
            try {
                ingestOne(dto, source);
            } catch (Exception e) {
                // one bad record shouldn't kill the whole batch
                log.error("Failed to ingest scraped drive: {}", dto, e);
            }
        }
    }

    private void ingestOne(ScrapedDriveDTO dto, DriveSource source) {
        if (dto.company() == null || dto.role() == null) {
            log.warn("Skipping scraped drive with missing company/role: {}", dto);
            return;
        }

        String fingerprint = fingerprintGenerator.generate(
            dto.company(), dto.role(), dto.location()
        );

        if (driveRepository.existsByFingerprint(fingerprint)) {
            log.debug("Skipping duplicate drive (fingerprint already known): {} - {}",
                dto.company(), dto.role());
            return;
        }

        boolean deadlineWasGuessed = false;
        LocalDate deadline = parseDeadline(dto.applicationDeadline());
        if (deadline == null) {
            deadline = LocalDate.now().plusDays(FALLBACK_DEADLINE_DAYS_OUT);
            deadlineWasGuessed = true;
        }

        Drive drive = Drive.builder()
            .companyName(dto.company())
            .jobRole(dto.role())
            .location(dto.location())
            .jobDescription(dto.description())
            .applyLink(dto.sourceUrl())
            .deadline(deadline)
            .fingerprint(fingerprint)
            .deadlineGuessed(deadlineWasGuessed)
            .reviewStatus(ReviewStatus.PENDING_REVIEW)
            .source(source)
            .status(DriveStatus.PENDING) // never shown publicly until admin approves
            .isFeatured(false)
            .viewCount(0)
            .build();

        driveRepository.save(drive);
        log.info("Ingested new drive [{}] {} - {} (deadline guessed: {})",
            source, dto.company(), dto.role(), deadlineWasGuessed);
    }

    private LocalDate parseDeadline(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            log.warn("Could not parse deadline '{}', will use fallback", raw);
            return null;
        }
    }
}