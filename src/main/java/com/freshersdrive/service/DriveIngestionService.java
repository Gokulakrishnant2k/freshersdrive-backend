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
 * Single entry point both discovery paths (RSS-based and Gemini-based)
 * funnel through, so dedup logic is consistent regardless of source.
 *
 * IMPORTANT: this does NOT make a drive publicly visible.
 * Every row created here sits at ReviewStatus.PENDING_REVIEW until an admin
 * approves it (see DriveReviewService), at which point a real DriveStatus
 * is assigned and it becomes visible like any manually-added drive.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriveIngestionService {

    private static final int FALLBACK_DEADLINE_DAYS_OUT = 30;

    /**
     * Used when the DTO's category string is missing, blank, or doesn't match
     * any JobCategory constant (typo, Gemini hallucination, unknown RSS value).
     * Getting a drive saved with an approximate category beats losing it
     * entirely — an admin reviews every record before it goes live anyway.
     */
    private static final JobCategory FALLBACK_CATEGORY = JobCategory.OTHERS;

    private final DriveRepository driveRepository;
    private final DriveFingerprintGenerator fingerprintGenerator;

    // ── Public API ─────────────────────────────────────────────────────────

    public void ingest(List<ScrapedDriveDTO> scrapedDrives, DriveSource source) {
        for (ScrapedDriveDTO dto : scrapedDrives) {
            try {
                ingestOne(dto, source);
            } catch (Exception e) {
                // one bad record must not kill the whole batch
                log.error("Failed to ingest scraped drive: {}", dto, e);
            }
        }
    }

    // ── Core logic ─────────────────────────────────────────────────────────

    private void ingestOne(ScrapedDriveDTO dto, DriveSource source) {
        if (dto.company() == null || dto.role() == null) {
            log.warn("Skipping scraped drive with missing company/role: {}", dto);
            return;
        }

        String fingerprint = fingerprintGenerator.generate(
                dto.company(), dto.role(), dto.location()
        );

        if (driveRepository.existsByFingerprint(fingerprint)) {
            log.debug("Skipping duplicate drive (fingerprint match): {} - {}",
                    dto.company(), dto.role());
            return;
        }

        boolean deadlineWasGuessed = false;
        LocalDate deadline = parseDeadline(dto.applicationDeadline());
        if (deadline == null) {
            deadline = LocalDate.now().plusDays(FALLBACK_DEADLINE_DAYS_OUT);
            deadlineWasGuessed = true;
        }

        JobCategory category    = resolveCategory(dto);
        String      eligibility = resolveEligibility(dto);

        Drive drive = Drive.builder()
                .companyName(dto.company())
                .jobRole(dto.role())
                .location(dto.location())
                .jobDescription(dto.description())
                .applyLink(dto.sourceUrl())
                .deadline(deadline)
                .category(category)
                .eligibleDegrees(eligibility)   // ← new: maps eligibility → existing column
                .fingerprint(fingerprint)
                .deadlineGuessed(deadlineWasGuessed)
                .reviewStatus(ReviewStatus.PENDING_REVIEW)
                .source(source)
                .status(DriveStatus.PENDING)    // never shown publicly until admin approves
                .isFeatured(false)
                .viewCount(0)
                .build();

        driveRepository.save(drive);
        log.info("Ingested new drive [{}] {} - {} (category: {}, eligibility: {}, deadline guessed: {})",
                source, dto.company(), dto.role(), category, eligibility, deadlineWasGuessed);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    /**
     * Validates the free-text category string against the real enum.
     * Case-insensitive and trims whitespace before matching, so minor
     * inconsistencies in Gemini output don't cause unnecessary fallbacks.
     */
    private JobCategory resolveCategory(ScrapedDriveDTO dto) {
        String raw = dto.category();
        if (raw == null || raw.isBlank()) {
            log.warn("No category for {} - {}, defaulting to {}",
                    dto.company(), dto.role(), FALLBACK_CATEGORY);
            return FALLBACK_CATEGORY;
        }
        try {
            return JobCategory.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Unrecognised category '{}' for {} - {}, defaulting to {}",
                    raw, dto.company(), dto.role(), FALLBACK_CATEGORY);
            return FALLBACK_CATEGORY;
        }
    }

    /**
     * Resolves eligibility to a non-null string for Drive.eligibleDegrees.
     * Null / blank DTOs (e.g. Gemini-sourced) fall back to "Any Graduate"
     * so the column is never left empty, and the admin can refine if needed.
     */
    private String resolveEligibility(ScrapedDriveDTO dto) {
        String raw = dto.eligibility();
        if (raw == null || raw.isBlank()) {
            return "Any Graduate";
        }
        return raw.trim();
    }

    /**
     * Accepts ISO-8601 ("YYYY-MM-DD") only — that's what both Gemini and
     * the RSS service are expected to produce. Anything else is treated as
     * unparseable and triggers the fallback deadline in the caller.
     */
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