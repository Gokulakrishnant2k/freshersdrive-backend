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

    private final DriveDiscoveryService discoveryService;
    private final DriveIngestionService ingestionService;
    private final RssFeedDiscoveryService rssFeedDiscoveryService;

    @Scheduled(cron = "0 0 */6 * * *") // every 6 hours = 4 Gemini calls/day
    public void runDiscovery() {
        log.info("Starting scheduled drive discovery run...");

        // AI-powered search (1 Gemini call)
        try {
            List<ScrapedDriveDTO> searchResults = discoveryService.discoverNewDrives();
            ingestionService.ingest(searchResults, DriveSource.AI_SEARCH);
            log.info("AI_SEARCH path returned {} candidate(s)", searchResults.size());
        } catch (Exception e) {
            log.error("AI_SEARCH discovery path failed", e);
        }

        // RSS feeds (free, no API calls)
        try {
            List<ScrapedDriveDTO> rssResults = rssFeedDiscoveryService.discoverFromRssFeeds();
            ingestionService.ingest(rssResults, DriveSource.RSS_FEED);
            log.info("RSS_FEED path returned {} candidate(s)", rssResults.size());
        } catch (Exception e) {
            log.error("RSS_FEED discovery path failed", e);
        }

        log.info("Drive discovery run complete.");
    }
}