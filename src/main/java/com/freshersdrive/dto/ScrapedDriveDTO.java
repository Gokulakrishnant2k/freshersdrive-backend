package com.freshersdrive.dto;

/**
 * Maps directly to the JSON shape Gemini is prompted to return.
 * Field names here are independent of the Drive entity's field names —
 * mapping happens explicitly in DriveIngestionService.
 */
public record ScrapedDriveDTO(
    String company,
    String role,
    String location,
    String applicationDeadline, // "YYYY-MM-DD" string or null; parsed to LocalDate at save time
    String sourceUrl,
    String description,
    String category // expected to be one of the JobCategory enum names; validated/mapped at save time
) {}