package com.freshersdrive.dto;

/**
 * Maps directly to the JSON shape Gemini is prompted to return,
 * and also used by RssFeedDiscoveryService for non-AI scraped listings.
 *
 * Field names here are independent of the Drive entity's field names —
 * mapping happens explicitly in DriveIngestionService.
 *
 * eligibility — human-readable degree/stream string produced by
 *   RssFeedDiscoveryService.extractEligibility(), e.g. "B.E/B.Tech".
 *   Stored in Drive.eligibleDegrees at ingestion time.
 *   Nullable: Gemini-sourced DTOs may omit it; ingestion handles null gracefully.
 */
public record ScrapedDriveDTO(
        String company,
        String role,
        String location,
        String applicationDeadline, // "YYYY-MM-DD" or null; parsed to LocalDate at save time
        String sourceUrl,
        String description,
        String category,            // must match a JobCategory constant; falls back to OTHERS
        String eligibility          // nullable — maps to Drive.eligibleDegrees
) {}