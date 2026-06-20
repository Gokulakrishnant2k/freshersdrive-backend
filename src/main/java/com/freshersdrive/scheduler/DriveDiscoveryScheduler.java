package com.freshersdrive.scheduler;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.freshersdrive.enums.DriveSource;
import com.freshersdrive.service.DriveDiscoveryService;
import com.freshersdrive.service.DriveIngestionService;
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

    @Scheduled(cron = "0 0 */6 * * *") // every 6 hours
    public void runDiscovery() {
        log.info("Starting scheduled drive discovery run...");

        try {
            List<ScrapedDriveDTO> searchResults = discoveryService.discoverNewDrives();
            ingestionService.ingest(searchResults, DriveSource.AI_SEARCH);
            log.info("AI_SEARCH path returned {} candidate(s)", searchResults.size());
        } catch (Exception e) {
            log.error("AI_SEARCH discovery path failed", e);
        }

        try {
            List<ScrapedDriveDTO> urlResults = discoveryService.discoverFromUrls();
            ingestionService.ingest(urlResults, DriveSource.AI_URL_CONTEXT);
            log.info("AI_URL_CONTEXT path returned {} candidate(s)", urlResults.size());
        } catch (Exception e) {
            log.error("AI_URL_CONTEXT discovery path failed", e);
        }

        log.info("Drive discovery run complete.");
    }
}