package com.freshersdrive.scheduler;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.freshersdrive.enums.DriveSource;
import com.freshersdrive.service.DriveDiscoveryService;
import com.freshersdrive.service.DriveIngestionService;
import com.freshersdrive.service.RssFeedDiscoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DriveDiscoveryScheduler {

    private final DriveDiscoveryService   discoveryService;
    private final DriveIngestionService   ingestionService;
    private final RssFeedDiscoveryService rssFeedDiscoveryService;

    // 4 Gemini calls/day — free tier safe
    @Scheduled(cron = "0 0 */6 * * *")
    public void runAiDiscovery() {
        log.info("Starting scheduled AI discovery run...");
        try {
            List<ScrapedDriveDTO> searchResults = discoveryService.discoverNewDrives();
            ingestionService.ingest(searchResults, DriveSource.AI_SEARCH);
            log.info("AI_SEARCH path returned {} candidate(s)", searchResults.size());
        } catch (Exception e) {
            log.error("AI_SEARCH discovery path failed", e);
        }
        log.info("AI discovery run complete.");
    }

    // 8 Gemini calls/day — runs every 3 hours
    @Scheduled(cron = "0 0 */3 * * *")
    public void runRssDiscovery() {
        log.info("Starting scheduled RSS discovery run...");
        try {
            List<ScrapedDriveDTO> rssResults = rssFeedDiscoveryService.discoverFromRssFeeds();
            ingestionService.ingest(rssResults, DriveSource.RSS_FEED);
            log.info("RSS_FEED path returned {} candidate(s)", rssResults.size());
        } catch (Exception e) {
            log.error("RSS_FEED discovery path failed", e);
        }
        log.info("RSS discovery run complete.");
    }

    // Runs twice a day — uses no-arg overload so scheduler needs no extra URLs
    @Scheduled(cron = "0 0 */12 * * *")
    public void runUrlDiscovery() {
        log.info("Starting scheduled URL scraping run...");
        try {
            // No extraUrls — scheduler always uses the built-in TARGET_URLS list.
            // Custom URLs are only injected via AdminDiscoveryController (manual trigger).
            List<ScrapedDriveDTO> results = discoveryService.discoverFromUrls();
            ingestionService.ingest(results, DriveSource.AI_SEARCH);
            log.info("URL scraping path returned {} candidate(s)", results.size());
        } catch (Exception e) {
            log.error("URL scraping discovery path failed", e);
        }
        log.info("URL scraping run complete.");
    }
}