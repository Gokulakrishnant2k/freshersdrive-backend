package com.freshersdrive.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freshersdrive.dto.ScrapedDriveDTO;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * DriveDiscoveryService — Jsoup + Groq edition.
 *
 * Flow:
 *   1. Jsoup fetches the raw HTML of each target URL (free, no API key needed).
 *   2. We extract only the relevant text (job listings section) to stay under
 *      Groq's token limits.
 *   3. Groq (llama-3.3-70b-versatile) parses the text into structured JSON.
 *   4. Results are returned as List<ScrapedDriveDTO> for ingestion.
 *
 * Groq free tier: 30 req/min, 500K tokens/day — more than enough.
 */
@Slf4j
@Service
public class DriveDiscoveryService {

    // ── config ────────────────────────────────────────────────────────────────

    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Value("${groq.model:llama-3.3-70b-versatile}")
    private String groqModel;

    @Value("${gemini.dry-run:false}")
    private boolean dryRun;

    @Value("${gemini.url-batch-size:3}")
    private int urlBatchSize;

    // ── constants ─────────────────────────────────────────────────────────────

    private static final int    MAX_TEXT_CHARS       = 12_000; // ~3K tokens, safe for Groq
    private static final int    JSOUP_TIMEOUT_MS     = 15_000;
    private static final long   DELAY_BETWEEN_CALLS  = 3_000;  // 3s between Groq calls

    private static final String CATEGORY_HINT = """
            "category": exactly ONE of:
              IT_SOFTWARE, CORE_ENGINEERING, GOVERNMENT, BANKING, MANAGEMENT, INTERNSHIP, OTHERS
            """;

    private static final List<String> TARGET_URLS = List.of(
            "https://www.freshersworld.com/jobs/freshers",
            "https://www.freshersnow.com/category/job-notifications/",
            "https://unstop.com/jobs",
            "https://www.geeksforgeeks.org/jobs/",
            "https://www.sarkariresult.com/latestjob/",
            "https://www.freejobalert.com/latest-notifications/",
            "https://dare2compete.com/opportunities/jobs",
            "https://www.hirist.tech/jobs/fresher",
            "https://www.placementindia.com/fresher-jobs.htm",
            "https://www.rojgarresult.com/"
    );

    // ── dry-run fake data ─────────────────────────────────────────────────────

    private static final List<ScrapedDriveDTO> DRY_RUN_RESULTS = List.of(
        new ScrapedDriveDTO(
            "TCS", "Systems Engineer Trainee", "PAN India", "2025-08-31",
            "https://nextstep.tcs.com/campus",
            "Entry-level role for 2024/2025 BE/BTech graduates.",
            "IT_SOFTWARE", "B.E/B.Tech"
        ),
        new ScrapedDriveDTO(
            "Infosys", "Systems Engineer", "Bangalore, Pune, Hyderabad", "2025-07-30",
            "https://career.infosys.com/joblist",
            "Campus hiring for freshers with 0-1 year experience.",
            "IT_SOFTWARE", "B.E/B.Tech"
        )
    );

    // ── infrastructure ────────────────────────────────────────────────────────

    private final RestClient   restClient   = RestClient.create("https://api.groq.com");
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * AI search discovery — uses Groq to generate fresh drive listings
     * based on its training knowledge of Indian job sites.
     */
    public List<ScrapedDriveDTO> discoverNewDrives() {
        if (dryRun) {
            log.info("[DRY-RUN] discoverNewDrives() — returning fake drives.");
            return DRY_RUN_RESULTS;
        }
        if (!isGroqConfigured()) return List.of();

        log.info("Running AI_SEARCH discovery via Groq (model={})…", groqModel);

        String prompt = """
                You are a job listing extractor for Indian fresher job portals.

                Generate a list of realistic current campus placement drives and
                fresher job openings in India for 2024/2025 batch graduates.
                Include IT companies, core engineering, banks, PSUs, and startups.

                Return ONLY a JSON array (no preamble, no markdown, no extra text):
                [
                  {
                    "company": "string",
                    "role": "string",
                    "location": "string — city/cities or 'PAN India' or 'Remote'",
                    "applicationDeadline": "YYYY-MM-DD or null",
                    "sourceUrl": "string — direct career page URL",
                    "description": "string — 2-3 sentences about the role",
                    %s
                  }
                ]

                Return at least 8-10 results. Only freshers/0-2 years experience roles.
                """.formatted(CATEGORY_HINT);

        return callGroq(prompt);
    }

    /**
     * URL-based discovery — Jsoup fetches each page, Groq parses the text.
     */
    public List<ScrapedDriveDTO> discoverFromUrls() {
        return discoverFromUrls(null);
    }

    public List<ScrapedDriveDTO> discoverFromUrls(String extraUrlsCsv) {
        if (dryRun) {
            log.info("[DRY-RUN] discoverFromUrls() — returning fake drives.");
            return DRY_RUN_RESULTS;
        }
        if (!isGroqConfigured()) return List.of();

        List<String> allUrls = new ArrayList<>(TARGET_URLS);
        if (extraUrlsCsv != null && !extraUrlsCsv.isBlank()) {
            Arrays.stream(extraUrlsCsv.split(","))
                  .map(String::trim)
                  .filter(u -> u.startsWith("http"))
                  .forEach(allUrls::add);
        }

        List<String> batch = allUrls.subList(0, Math.min(urlBatchSize, allUrls.size()));
        List<ScrapedDriveDTO> allDrives = new ArrayList<>();

        for (int i = 0; i < batch.size(); i++) {
            String url = batch.get(i);
            log.info("Processing URL ({}/{}): {}", i + 1, batch.size(), url);

            try {
                String pageText = fetchPageText(url);
                if (pageText.isBlank()) {
                    log.warn("No text extracted from {}", url);
                    continue;
                }

                List<ScrapedDriveDTO> results = extractFromText(pageText, url);
                log.info("Found {} listing(s) from {}", results.size(), url);
                allDrives.addAll(results);

            } catch (Exception e) {
                log.warn("Failed to process {}: {}", url, e.getMessage());
            }

            if (i < batch.size() - 1) {
                sleep(DELAY_BETWEEN_CALLS);
            }
        }

        log.info("URL discovery complete — {} total drives found.", allDrives.size());
        return allDrives;
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Fetches a page with Jsoup and extracts clean text from job-related elements.
     * Truncates to MAX_TEXT_CHARS to stay within Groq token limits.
     */
    private String fetchPageText(String url) throws IOException {
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                           "AppleWebKit/537.36 (KHTML, like Gecko) " +
                           "Chrome/120.0.0.0 Safari/537.36")
                .timeout(JSOUP_TIMEOUT_MS)
                .get();

        // Try to find the main content area first
        StringBuilder sb = new StringBuilder();

        // Priority selectors for job listing pages
        String[] contentSelectors = {
            "main", "article", ".jobs", ".job-list", ".listings",
            ".content", "#content", ".results", ".job-results",
            "[class*=job]", "[class*=listing]", "[id*=job]"
        };

        for (String selector : contentSelectors) {
            Elements elements = doc.select(selector);
            if (!elements.isEmpty()) {
                for (Element el : elements) {
                    sb.append(el.text()).append("\n");
                }
                if (sb.length() > 500) break; // found good content
            }
        }

        // Fall back to full body text if selectors found nothing
        if (sb.length() < 500) {
            sb.setLength(0);
            sb.append(doc.body().text());
        }

        String text = sb.toString().trim();

        // Truncate to avoid exceeding Groq token limits
        if (text.length() > MAX_TEXT_CHARS) {
            text = text.substring(0, MAX_TEXT_CHARS);
        }

        return text;
    }

    /**
     * Sends extracted page text to Groq and parses the JSON response.
     */
    private List<ScrapedDriveDTO> extractFromText(String pageText, String sourceUrl) {
        String prompt = """
                You are a job listing extractor. Below is text scraped from a job portal page.

                Extract all fresher / entry-level job openings or campus placement drives
                for candidates with 0-2 years experience or 2023/2024/2025 batch graduates.

                Source URL: %s

                Page content:
                ---
                %s
                ---

                Return ONLY a JSON array (no preamble, no markdown fences, no extra text).
                If no fresher jobs are found, return [].

                JSON shape:
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
                """.formatted(sourceUrl, pageText, sourceUrl, CATEGORY_HINT);

        return callGroq(prompt);
    }

    /**
     * Calls Groq chat completions API and parses the JSON array from the response.
     */
    private List<ScrapedDriveDTO> callGroq(String userPrompt) {
        try {
            Map<String, Object> body = Map.of(
                "model", groqModel,
                "messages", List.of(
                    Map.of("role", "system",
                           "content", "You are a precise JSON extractor. Always return valid JSON arrays only. No markdown, no explanations."),
                    Map.of("role", "user", "content", userPrompt)
                ),
                "temperature", 0.2,
                "max_tokens", 4096
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/openai/v1/chat/completions")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + groqApiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return parseGroqResponse(response);

        } catch (Exception e) {
            log.error("Groq API call failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<ScrapedDriveDTO> parseGroqResponse(Map<String, Object> response) {
        if (response == null) {
            log.error("Groq returned null response");
            return List.of();
        }

        if (response.containsKey("error")) {
            Map<String, Object> err = (Map<String, Object>) response.get("error");
            log.error("Groq API error: {}", err.get("message"));
            return List.of();
        }

        String text = null;
        try {
            List<Map<String, Object>> choices =
                    (List<Map<String, Object>>) response.get("choices");

            if (choices == null || choices.isEmpty()) {
                log.error("Groq response had no choices");
                return List.of();
            }

            Map<String, Object> message =
                    (Map<String, Object>) choices.get(0).get("message");
            text = ((String) message.get("content")).trim();

            if (text.isBlank()) {
                log.error("Groq returned empty content");
                return List.of();
            }

            // Strip markdown fences if present
            text = stripMarkdownFences(text);

            // Extract JSON array
            int start = text.indexOf('[');
            int end   = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end < start) {
                log.error("No JSON array in Groq response. Raw: {}", text);
                return List.of();
            }

            String json = text.substring(start, end + 1);
            List<ScrapedDriveDTO> results =
                    objectMapper.readValue(json, new TypeReference<List<ScrapedDriveDTO>>() {});

            log.info("Parsed {} drive(s) from Groq response.", results.size());
            return results;

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Groq JSON. Raw text:\n{}", text, e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error parsing Groq response", e);
            return List.of();
        }
    }

    private boolean isGroqConfigured() {
        if (groqApiKey == null || groqApiKey.isBlank()
                || groqApiKey.equals("${groq.api.key}")) {
            log.error("Groq API key is not configured. " +
                      "Set GROQ_API_KEY as an environment variable on Render.");
            return false;
        }
        return true;
    }

    private static String stripMarkdownFences(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int newline = t.indexOf('\n');
            if (newline != -1) t = t.substring(newline + 1).trim();
        }
        if (t.endsWith("```")) {
            t = t.substring(0, t.lastIndexOf("```")).trim();
        }
        return t;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}