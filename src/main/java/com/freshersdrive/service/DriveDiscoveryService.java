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
import java.util.List;
import java.util.Map;

/**
 * Calls the Gemini API to discover new drive listings two ways:
 *  1. discoverNewDrives()  - open-ended: Google Search + url_context combined,
 *                            for finding postings on sites you haven't listed.
 *  2. discoverFromUrls()   - targeted: url_context only, against a fixed list
 *                            of known fresher job boards that render server-side HTML.
 *
 * Both return ScrapedDriveDTO lists; DriveIngestionService handles dedup + saving.
 *
 * NOTE ON URL SELECTION:
 * Only server-side rendered sites work with url_context. JS-heavy sites like
 * LinkedIn, Naukri, and Instahyre are excluded because Gemini cannot read them.
 * The open-ended search in discoverNewDrives() covers those indirectly.
 */
@Slf4j
@Service
public class DriveDiscoveryService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    private static final String CATEGORY_INSTRUCTIONS = """
        "category": pick exactly ONE of the following values (return the
        value exactly as written, no other text):
          - IT_SOFTWARE: software/IT/tech roles (dev, QA, data, support, etc.)
          - CORE_ENGINEERING: non-IT engineering (mechanical, civil, electrical, etc.)
          - GOVERNMENT: government/PSU recruitment
          - BANKING: banking/finance/insurance sector roles
          - MANAGEMENT: MBA/management trainee/business roles
          - INTERNSHIP: explicitly an internship (any field)
          - OFF_CAMPUS: doesn't clearly fit any of the above, or fit is unclear
        If a listing could fit more than one (e.g. a software internship),
        prefer INTERNSHIP only if "internship" is explicit in the posting;
        otherwise classify by field.
        """;

    /**
     * Sites that reliably work with Gemini url_context (server-side rendered,
     * no login wall, actively lists fresher/entry-level jobs in India).
     *
     * Excluded (JS-rendered or login-required):
     *   - linkedin.com       → login required
     *   - naukri.com         → JS rendered
     *   - instahyre.com      → JS rendered
     *   - internshala.com    → JS rendered
     *   - shine.com          → JS rendered
     */
    private static final List<String> TARGET_URLS = List.of(
        // Fresher-specific job boards
        "https://www.freshersworld.com/jobs/freshers",
        "https://www.fresherslive.com/latest-jobs",
        "https://www.freshersnow.com/category/job-notifications/",
        "https://www.freshersvoice.com/category/jobs/it-jobs/",

        // Campus / off-campus drives aggregators
        "https://dare2compete.com/opportunities/jobs",
        "https://unstop.com/jobs",
        "https://www.placementindia.com/fresher-jobs.htm",

        // Company career pages that render server-side
        "https://nextstep.tcs.com/campus",
        "https://career.infosys.com/joblist",
        "https://careers.wipro.com/careers-home/jobs",
        "https://careers.cognizant.com/global/en/search-results",
        "https://www.hcltech.com/careers/students-and-freshers",
        "https://campus.accenture.com/in/en/students/pages/jobs.aspx",

        // General job boards with good server-side rendering
        "https://www.monsterindia.com/freshers-jobs.html",
        "https://www.timesjobs.com/candidate/job-search.html?searchType=personalizedSearch&from=submit&txtKeywords=fresher&txtLocation=India",
        "https://www.wisdomjobs.com/e-university/freshers-jobs-in-india.html",
        "https://www.hirist.tech/jobs/fresher",
        "https://www.geeksforgeeks.org/jobs/",

        // Govt / PSU jobs
        "https://www.sarkariresult.com/latestjob/",
        "https://www.freejobalert.com/latest-notifications/",
        "https://www.rojgarresult.com/"
    );

    private final RestClient restClient = RestClient.create(
        "https://generativelanguage.googleapis.com"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Open-ended discovery: uses Google Search grounding to find fresher drives
     * posted anywhere on the web in the last 7 days, then reads promising pages
     * with url_context before extracting details.
     *
     * This is the primary discovery path and covers sites that TARGET_URLS
     * cannot reach (LinkedIn, Naukri, etc.) indirectly via search snippets.
     */
    public List<ScrapedDriveDTO> discoverNewDrives() {
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
                "company": "string — company name",
                "role": "string — exact job title",
                "location": "string — city/cities or 'PAN India' or 'Remote'",
                "applicationDeadline": "YYYY-MM-DD or null if not mentioned",
                "sourceUrl": "string — direct link to the job posting",
                "description": "string — 2-3 sentences describing role and eligibility",
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
            "tools", List.of(
                Map.of("google_search", Map.of()),
                Map.of("url_context", Map.of())
            )
        );

        log.info("Running open-ended AI_SEARCH discovery...");
        return callGemini(body);
    }

    /**
     * Targeted discovery: reads each URL in TARGET_URLS using url_context
     * and extracts current job listings from it.
     *
     * Works best on server-side rendered pages. JS-heavy sites are excluded
     * from TARGET_URLS intentionally — they're covered by discoverNewDrives().
     */
    public List<ScrapedDriveDTO> discoverFromUrls() {
        List<ScrapedDriveDTO> allDrives = new ArrayList<>();
        for (String url : TARGET_URLS) {
            log.info("Checking URL: {}", url);
            List<ScrapedDriveDTO> results = extractListingsFromUrl(url);
            log.info("Found {} listing(s) from {}", results.size(), url);
            allDrives.addAll(results);
        }
        return allDrives;
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
                "company": "string — company name",
                "role": "string — exact job title",
                "location": "string — city/cities or 'PAN India' or 'Remote'",
                "applicationDeadline": "YYYY-MM-DD or null if not mentioned",
                "sourceUrl": "%s",
                "description": "string — 2-3 sentences describing role and eligibility",
                %s
              }
            ]

            If there are no fresher openings on the page, return [].
            """.formatted(url, url, CATEGORY_INSTRUCTIONS);

        Map<String, Object> body = Map.of(
            "contents", List.of(Map.of(
                "parts", List.of(Map.of("text", prompt))
            )),
            "tools", List.of(Map.of("url_context", Map.of()))
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
            log.error("Gemini API call failed: {}", e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Extracts the JSON array from Gemini's response text.
     *
     * Grounded responses (google_search / url_context enabled) frequently
     * ignore "return ONLY JSON" instructions and add a leading sentence or
     * trailing commentary, so we scan for the outermost [ ... ] instead of
     * just stripping markdown fences.
     */
    @SuppressWarnings("unchecked")
    private List<ScrapedDriveDTO> parseGeminiResponse(Map<String, Object> response) {
        if (response == null) {
            log.error("Gemini returned a null response body");
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

            // Check finish reason — SAFETY / RECITATION / MAX_TOKENS produce no content
            String finishReason = (String) candidate.get("finishReason");
            if (finishReason != null && !finishReason.equals("STOP") && !finishReason.equals("MAX_TOKENS")) {
                log.warn("Gemini finished with reason: {}. Skipping.", finishReason);
                return List.of();
            }

            Map<String, Object> content = (Map<String, Object>) candidate.get("content");
            if (content == null) {
                log.error("Gemini candidate had no content. Finish reason: {}", finishReason);
                return List.of();
            }

            List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");

            if (parts == null || parts.isEmpty()) {
                log.error("Gemini content had no parts.");
                return List.of();
            }

            // Concatenate all text parts (grounded responses sometimes split across parts)
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> part : parts) {
                Object t = part.get("text");
                if (t instanceof String) sb.append(t);
            }
            text = sb.toString().trim();

            if (text.isBlank()) {
                log.error("Gemini response had no text content.");
                return List.of();
            }

            // Extract outermost JSON array
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
            log.error("Failed to parse Gemini JSON. Raw text was: {}", text, e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected error parsing Gemini response: {}", response, e);
            return List.of();
        }
    }
}