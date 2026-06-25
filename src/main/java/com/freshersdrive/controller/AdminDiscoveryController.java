package com.freshersdrive.controller;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.freshersdrive.enums.DriveSource;
import com.freshersdrive.service.DriveDiscoveryService;
import com.freshersdrive.service.DriveIngestionService;
import com.freshersdrive.service.RssFeedDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints that allow manual triggering of each discovery path
 * from the dashboard, without waiting for the scheduler.
 *
 * All endpoints require ADMIN role.
 * All endpoints return a JSON object: { "ingested": N, "status": "queued_for_review" }
 *
 * The URL trigger accepts an optional ?extraUrls= query param (comma-separated)
 * so the admin can add custom pages from the dashboard URL input widget.
 */
@Slf4j
@RestController
@RequestMapping("/admin/discovery")
@RequiredArgsConstructor
public class AdminDiscoveryController {

    private final DriveDiscoveryService   discoveryService;
    private final DriveIngestionService   ingestionService;
    private final RssFeedDiscoveryService rssFeedDiscoveryService;

    /**
     * POST /api/admin/discovery/rss
     *
     * Triggers one full RSS discovery run immediately.
     * Equivalent to what the scheduler runs every 3 hours.
     */
    @PostMapping("/rss")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerRss() {
        log.info("Manual RSS discovery triggered from admin panel");
        try {
            List<ScrapedDriveDTO> results = rssFeedDiscoveryService.discoverFromRssFeeds();
            ingestionService.ingest(results, DriveSource.RSS_FEED);
            log.info("Manual RSS run complete — {} candidate(s) queued", results.size());
            return ok(results.size());
        } catch (Exception e) {
            log.error("Manual RSS discovery failed", e);
            return error(e.getMessage());
        }
    }

    /**
     * POST /api/admin/discovery/ai
     *
     * Triggers one Gemini Google Search grounding run immediately.
     * Requires gemini.dry-run=false and a valid GEMINI_API_KEY.
     */
    @PostMapping("/ai")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerAi() {
        log.info("Manual AI discovery triggered from admin panel");
        try {
            List<ScrapedDriveDTO> results = discoveryService.discoverNewDrives();
            ingestionService.ingest(results, DriveSource.AI_SEARCH);
            log.info("Manual AI run complete — {} candidate(s) queued", results.size());
            return ok(results.size());
        } catch (Exception e) {
            log.error("Manual AI discovery failed", e);
            return error(e.getMessage());
        }
    }

    /**
     * POST /api/admin/discovery/url
     * POST /api/admin/discovery/url?extraUrls=https://...,https://...
     *
     * Triggers URL scraping via Gemini urlContext tool.
     * The optional extraUrls param lets the admin supply custom pages
     * from the dashboard URL input widget — these are appended to the
     * built-in TARGET_URLS list for this run only.
     *
     * Requires gemini.dry-run=false and a valid GEMINI_API_KEY.
     */
    @PostMapping("/url")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> triggerUrl(
            @RequestParam(required = false) String extraUrls) {
        log.info("Manual URL discovery triggered from admin panel (extraUrls={})", extraUrls);
        try {
            List<ScrapedDriveDTO> results = discoveryService.discoverFromUrls(extraUrls);
            ingestionService.ingest(results, DriveSource.AI_SEARCH);
            log.info("Manual URL run complete — {} candidate(s) queued", results.size());
            return ok(results.size());
        } catch (Exception e) {
            log.error("Manual URL discovery failed", e);
            return error(e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ResponseEntity<Map<String, Object>> ok(int count) {
        return ResponseEntity.ok(Map.of(
            "ingested", count,
            "status",   "queued_for_review"
        ));
    }

    private ResponseEntity<Map<String, Object>> error(String message) {
        return ResponseEntity.internalServerError().body(Map.of(
            "ingested", 0,
            "status",   "error",
            "message",  message != null ? message : "unknown error"
        ));
    }
}