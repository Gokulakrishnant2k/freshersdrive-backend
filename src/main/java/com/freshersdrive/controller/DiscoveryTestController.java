package com.freshersdrive.controller;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.freshersdrive.enums.DriveSource;
import com.freshersdrive.service.DriveDiscoveryService;
import com.freshersdrive.service.DriveIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * TEMPORARY diagnostic endpoint. Triggers the same discovery + ingestion
 * logic as DriveDiscoveryScheduler, but on demand, so you can test without
 * waiting for the 6-hour cron. Returns the raw ScrapedDriveDTOs in the
 * response so you can see exactly what Gemini returned (including the
 * category it picked) without grepping logs.
 *
 * DELETE THIS or lock it behind admin auth before leaving it deployed -
 * as written it's unauthenticated, and every call costs a Gemini API call
 * and writes rows to your DB.
 */
@Slf4j
@RestController
@RequestMapping("/admin/discovery")
@RequiredArgsConstructor
public class DiscoveryTestController {

    private final DriveDiscoveryService discoveryService;
    private final DriveIngestionService ingestionService;

    @PostMapping("/run")
    public Map<String, Object> runNow() {
        log.info("Manually triggered drive discovery run...");

        List<ScrapedDriveDTO> searchResults = discoveryService.discoverNewDrives();
        ingestionService.ingest(searchResults, DriveSource.AI_SEARCH);
        log.info("AI_SEARCH path returned {} candidate(s)", searchResults.size());

        List<ScrapedDriveDTO> urlResults = discoveryService.discoverFromUrls();
        ingestionService.ingest(urlResults, DriveSource.AI_URL_CONTEXT);
        log.info("AI_URL_CONTEXT path returned {} candidate(s)", urlResults.size());

        log.info("Manual drive discovery run complete.");

        return Map.of(
            "aiSearchCandidateCount", searchResults.size(),
            "aiUrlContextCandidateCount", urlResults.size(),
            "aiSearchCandidates", searchResults,
            "aiUrlContextCandidates", urlResults
        );
    }
}