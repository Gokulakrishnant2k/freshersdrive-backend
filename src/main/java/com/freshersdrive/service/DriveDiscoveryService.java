package com.freshersdrive.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshersdrive.dto.ScrapedDriveDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class DriveDiscoveryService {

    // ------------------------------------------------------------------ config

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    /**
     * DRY-RUN MODE — set gemini.dry-run=false in application.properties (or
     * GEMINI_DRY_RUN=false as an env var on Render) to make real API calls.
     *
     * DEFAULT IS NOW FALSE — change to true only for local testing without quota.
     *
     * application.properties:
     *   gemini.dry-run=false   ← real calls (default)
     *   gemini.dry-run=true    ← safe, free, always works (local dev)
     */
    @Value("${gemini.dry-run:false}")
    private boolean dryRun;

    /**
     * How many TARGET_URLS to process per run.
     * Free tier = 15 RPM / ~1500 RPD.
     * Default 3 keeps a single run well under the per-minute limit.
     */
    @Value("${gemini.url-batch-size:3}")
    private int urlBatchSize;

    /**
     * Maximum consecutive Gemini failures before the run aborts.
     */
    @Value("${gemini.max-consecutive-failures:2}")
    private int maxConsecutiveFailures;

    // ------------------------------------------------------------------ constants

    private static final String CATEGORY_INSTRUCTIONS = """
            "category": pick exactly ONE of the following values (return the
            value exactly as written, no other text):
              - IT_SOFTWARE: software/IT/tech roles (dev, QA, data, support, etc.)
              - CORE_ENGINEERING_GENERAL: non-IT engineering (mechanical, civil, electrical, etc.)
              - GOVERNMENT: government/PSU recruitment
              - BANKING: banking/finance/insurance sector roles
              - MBA_GENERAL: MBA/management trainee/business roles
              - INTERNSHIP: explicitly an internship (any field)
              - OTHERS: doesn't clearly fit any of the above
            If a listing could fit more than one (e.g. a software internship),
            prefer INTERNSHIP only if "internship" is explicit in the posting;
            otherwise classify by field.
            """;

    // "OTHER" is returned by Gemini when grounding/search tools are used.
    private static final Set<String> ACCEPTABLE_FINISH_REASONS =
            Set.of("STOP", "MAX_TOKENS", "OTHER");

    /**
     * All candidate URLs. Only urlBatchSize are processed per run.
     */
    private static final List<String> TARGET_URLS = List.of(
            "https://www.freshersworld.com/jobs/freshers",
            "https://www.fresherslive.com/latest-jobs",
            "https://www.freshersnow.com/category/job-notifications/",
            "https://dare2compete.com/opportunities/jobs",
            "https://unstop.com/jobs",
            "https://www.placementindia.com/fresher-jobs.htm",
            "https://nextstep.tcs.com/campus",
            "https://career.infosys.com/joblist",
            "https://www.monsterindia.com/freshers-jobs.html",
            "https://www.timesjobs.com/candidate/job-search.html?searchType=personalizedSearch&from=submit&txtKeywords=fresher&txtLocation=India",
            "https://www.hirist.tech/jobs/fresher",
            "https://www.geeksforgeeks.org/jobs/",
            "https://www.sarkariresult.com/latestjob/",
            "https://www.freejobalert.com/latest-notifications/",
            "https://www.rojgarresult.com/"
    );

    // 15 s gap keeps us under the 5 RPM free-tier limit with room to spare.
    private static final long DELAY_BETWEEN_CALLS_MS = 15_000;

    // ── Dry-run fake data ──────────────────────────────────────────────────
    private static final List<ScrapedDriveDTO> DRY_RUN_RESULTS = List.of(
        new ScrapedDriveDTO(
            "TCS",
            "Systems Engineer Trainee",
            "PAN India",
            "2025-08-31",
            "https://nextstep.tcs.com/campus",
            "Entry-level role for 2024/2025 BE/BTech graduates. Covers application development and testing.",
            "IT_SOFTWARE",
            "B.E/B.Tech"
        ),
        new ScrapedDriveDTO(
            "Infosys",
            "Systems Engineer",
            "Bangalore, Pune, Hyderabad",
            "2025-07-30",
            "https://career.infosys.com/joblist",
            "Campus hiring for freshers with 0-1 year experience. Training provided.",
            "IT_SOFTWARE",
            "B.E/B.Tech"
        ),
        new ScrapedDriveDTO(
            "IBPS",
            "Probationary Officer",
            "PAN India",
            "2025-07-15",
            "https://www.ibps.in",
            "Government bank recruitment for graduates. Written exam + interview.",
            "BANKING",
            "Any Graduate"
        )
    );

    // ------------------------------------------------------------------ infrastructure

    private final RestClient restClient = RestClient.create(
            "https://generativelanguage.googleapis.com"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ public API

    /**
     * Uses Gemini's built-in Google Search grounding to discover fresh listings.
     */
    public List<ScrapedDriveDTO> discoverNewDrives() {
        if (dryRun) {
            log.info("[DRY-RUN] discoverNewDrives() — returning {} fake drives, no API call made.",
                    DRY_RUN_RESULTS.size());
            return DRY_RUN_RESULTS;
        }

        if (!isApiKeyConfigured()) return List.of();

        String prompt = """
                Search the web for campus placement drives, off-campus drives, and
                fresher / entry-level job openings posted in India in the last 7 days.
                Focus on IT companies, core engineering firms, banks, PSUs, and startups
                hiring 2024 and 2025 batch graduates.

                For each promising result, open the actual job posting page and read its
                full content before extracting details, so the listing reflects the real
                page rather than just a search snippet.

                Only include listings where:
                - The role is for freshers or 0-2 years experience
                - The posting appears to be from the last 7 days
                - You can extract a valid apply link

                Return ONLY a JSON array (no preamble, no markdown fences, no extra text)
                in this exact shape:
                [
                  {
                    "company": "string",
                    "role": "string",
                    "location": "string — city/cities or 'PAN India' or 'Remote'",
                    "applicationDeadline": "YYYY-MM-DD or null",
                    "sourceUrl": "string — direct link to the job posting",
                    "description": "string — 2-3 sentences",
                    %s
                  }
                ]

                If you find nothing recent, return an empty array [].
                Aim for at least 5-10 results if available.
                """.formatted(CATEGORY_INSTRUCTIONS);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "tools", List.of(Map.of("googleSearch", Map.of()))
        );

        log.info("Running AI_SEARCH discovery (model={})…", model);
        return callGemini(body);
    }

    /**
     * Reads TARGET_URLS (plus any extraUrls) via the url_context tool.
     *
     * Called by the scheduler (no extra URLs) and by the admin trigger endpoint
     * (which may pass custom URLs from the dashboard input).
     */
    public List<ScrapedDriveDTO> discoverFromUrls() {
        return discoverFromUrls(null);
    }

    /**
     * @param extraUrlsCsv comma-separated additional URLs to process this run,
     *                     on top of TARGET_URLS — may be null or blank.
     */
    public List<ScrapedDriveDTO> discoverFromUrls(String extraUrlsCsv) {
        if (dryRun) {
            log.info("[DRY-RUN] discoverFromUrls() — returning {} fake drives, no API call made.",
                    DRY_RUN_RESULTS.size());
            return DRY_RUN_RESULTS;
        }

        if (!isApiKeyConfigured()) return List.of();

        // Build the combined URL list: built-in defaults + any custom additions
        List<String> allUrls = new ArrayList<>(TARGET_URLS);
        if (extraUrlsCsv != null && !extraUrlsCsv.isBlank()) {
            Arrays.stream(extraUrlsCsv.split(","))
                  .map(String::trim)
                  .filter(u -> u.startsWith("http"))
                  .forEach(allUrls::add);
            log.info("Extra URLs added from admin panel: {}",
                     allUrls.subList(TARGET_URLS.size(), allUrls.size()));
        }

        List<ScrapedDriveDTO> allDrives = new ArrayList<>();
        List<String> batch = allUrls.subList(0, Math.min(urlBatchSize, allUrls.size()));
        int consecutiveFailures = 0;

        for (int i = 0; i < batch.size(); i++) {
            if (consecutiveFailures >= maxConsecutiveFailures) {
                log.error("Aborting URL discovery after {} consecutive failures. " +
                          "Check your API key and quota. Processed {}/{} URLs.",
                          consecutiveFailures, i, batch.size());
                break;
            }

            String url = batch.get(i);
            log.info("Checking URL ({}/{}): {}", i + 1, batch.size(), url);

            List<ScrapedDriveDTO> results = extractListingsFromUrl(url);

            if (results.isEmpty()) {
                consecutiveFailures++;
                log.warn("No results from {} (consecutive failures: {})", url, consecutiveFailures);
            } else {
                consecutiveFailures = 0;
                log.info("Found {} listing(s) from {}", results.size(), url);
                allDrives.addAll(results);
            }

            if (i < batch.size() - 1) {
                try {
                    log.debug("Waiting {}ms before next Gemini call…", DELAY_BETWEEN_CALLS_MS);
                    Thread.sleep(DELAY_BETWEEN_CALLS_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("URL discovery interrupted after {} URLs", i + 1);
                    break;
                }
            }
        }

        return allDrives;
    }

    // ------------------------------------------------------------------ private helpers

    private boolean isApiKeyConfigured() {
        if (apiKey == null || apiKey.isBlank()
                || apiKey.equals("${gemini.api.key}")
                || apiKey.startsWith("your_")) {
            log.error("Gemini API key is not configured. " +
                      "Set gemini.api.key in application.properties or " +
                      "GEMINI_API_KEY as an environment variable on Render. " +
                      "No API call will be made.");
            return false;
        }
        return true;
    }

    private List<ScrapedDriveDTO> extractListingsFromUrl(String url) {
        String prompt = """
                Read the page at this URL: %s

                List every fresher / entry-level job opening or campus placement drive
                currently listed on this page. Only include roles for candidates with
                0-2 years of experience or recent graduates (2023, 2024, 2025 batch).

                Return ONLY a JSON array (no preamble, no markdown fences, no extra text)
                in this exact shape:
                [
                  {
                    "company": "string",
                    "role": "string",
                    "location": "string — city/cities or 'PAN India' or 'Remote'",
                    "applicationDeadline": "YYYY-MM-DD or null",
                    "sourceUrl": "%s",
                    "description": "string — 2-3 sentences",
                    %s
                  }
                ]

                If there are no fresher openings on the page, return [].
                """.formatted(url, url, CATEGORY_INSTRUCTIONS);

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of(
                        "parts", List.of(Map.of("text", prompt))
                )),
                "tools", List.of(Map.of("urlContext", Map.of()))
        );

        return callGemini(body);
    }

    private List<ScrapedDriveDTO> callGemini(Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/v1beta/models/" + model + ":generateContent?key=" + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return parseGeminiResponse(response);
        } catch (Exception e) {
            log.error("Gemini API call failed: {} — cause: {}", e.getMessage(),
                    e.getCause() != null ? e.getCause().getMessage() : "none", e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ScrapedDriveDTO> parseGeminiResponse(Map<String, Object> response) {
        if (response == null) {
            log.error("Gemini returned a null response body");
            return List.of();
        }

        if (response.containsKey("error")) {
            Map<String, Object> err = (Map<String, Object>) response.get("error");
            log.error("Gemini API error — code: {}, message: {}, status: {}",
                    err.get("code"), err.get("message"), err.get("status"));
            return List.of();
        }

        String text = null;
        try {
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) response.get("candidates");

            if (candidates == null || candidates.isEmpty()) {
                log.error("Gemini response had no candidates. Full response: {}", response);
                return List.of();
            }

            Map<String, Object> candidate = candidates.get(0);
            String finishReason = (String) candidate.get("finishReason");

            if (finishReason != null && !ACCEPTABLE_FINISH_REASONS.contains(finishReason)) {
                log.warn("Gemini finished with unacceptable reason: {}. Full candidate: {}",
                        finishReason, candidate);
                return List.of();
            }

            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            if (content == null) {
                log.error("Gemini candidate had no content. finishReason={}", finishReason);
                return List.of();
            }

            List<Map<String, Object>> parts =
                    (List<Map<String, Object>>) content.get("parts");

            if (parts == null || parts.isEmpty()) {
                log.error("Gemini content had no parts.");
                return List.of();
            }

            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> part : parts) {
                Object t = part.get("text");
                if (t instanceof String s) sb.append(s);
            }
            text = sb.toString().trim();

            if (text.isBlank()) {
                log.error("Gemini response had no text content. Parts were: {}", parts);
                return List.of();
            }

            text = stripMarkdownFences(text);

            int start = text.indexOf('[');
            int end   = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end < start) {
                log.error("No JSON array found in Gemini response. Raw text: {}", text);
                return List.of();
            }

            String json = text.substring(start, end + 1);
            List<ScrapedDriveDTO> results =
                    objectMapper.readValue(json, new TypeReference<List<ScrapedDriveDTO>>() {});

            log.info("Parsed {} drive(s) from Gemini response.", results.size());
            return results;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini JSON. Raw text was:\n{}", text, e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error parsing Gemini response: {}", response, e);
            return List.of();
        }
    }

    private static String stripMarkdownFences(String text) {
        String t = text;
        if (t.startsWith("```")) {
            int newline = t.indexOf('\n');
            if (newline != -1) t = t.substring(newline + 1);
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.lastIndexOf("```")).trim();
        }
        return t.trim();
    }
}