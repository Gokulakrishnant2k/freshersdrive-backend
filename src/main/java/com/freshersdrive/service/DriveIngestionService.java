package com.freshersdrive.service;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveSource;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.JobCategory;
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

    // Used when Gemini's "category" field is missing or doesn't match a real
    // JobCategory constant (typo, hallucinated value, etc.). AI-discovered
    // listings are inherently off-campus in nature, so this is a reasonable
    // default - revisit if you want stricter handling (e.g. drop the record
    // instead of guessing).
    private static final JobCategory FALLBACK_CATEGORY = JobCategory.OTHERS;

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

        JobCategory category = resolveCategory(dto);

        Drive drive = Drive.builder()
            .companyName(dto.company())
            .jobRole(dto.role())
            .location(dto.location())
            .jobDescription(dto.description())
            .applyLink(dto.sourceUrl())
            .deadline(deadline)
            .category(category)
            .fingerprint(fingerprint)
            .deadlineGuessed(deadlineWasGuessed)
            .reviewStatus(ReviewStatus.PENDING_REVIEW)
            .source(source)
            .status(DriveStatus.PENDING) // never shown publicly until admin approves
            .isFeatured(false)
            .viewCount(0)
            .build();

        driveRepository.save(drive);
        log.info("Ingested new drive [{}] {} - {} (category: {}, deadline guessed: {})",
            source, dto.company(), dto.role(), category, deadlineWasGuessed);
    }

    /**
     * Validates Gemini's free-text category guess against the real enum.
     * Anything missing, mistyped, or hallucinated falls back to
     * FALLBACK_CATEGORY rather than failing the whole record - getting a
     * drive saved with an approximate category beats losing it entirely,
     * since an admin reviews every record before it goes live anyway.
     */
    private JobCategory resolveCategory(ScrapedDriveDTO dto) {
        String raw = dto.category();
        if (raw == null || raw.isBlank()) {
            log.warn("No category returned for {} - {}, defaulting to {}",
                dto.company(), dto.role(), FALLBACK_CATEGORY);
            return FALLBACK_CATEGORY;
        }
        try {
            return JobCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognized category '{}' for {} - {}, defaulting to {}",
                raw, dto.company(), dto.role(), FALLBACK_CATEGORY);
            return FALLBACK_CATEGORY;
        }
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