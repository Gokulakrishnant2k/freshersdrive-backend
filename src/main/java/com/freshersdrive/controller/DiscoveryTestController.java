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

        log.info("Manual drive discovery run complete.");

        return Map.of(
            "aiSearchCandidateCount", searchResults.size(),
            "aiSearchCandidates", searchResults
        );
    }
}