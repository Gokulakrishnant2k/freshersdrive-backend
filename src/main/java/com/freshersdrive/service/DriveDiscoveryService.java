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
import java.util.Set;

@Slf4j
@Service
public class DriveDiscoveryService {

    // ------------------------------------------------------------------ config

    @Value("${gemini.api.key}")
    private String apiKey;

    // FIX 1: default now matches application.properties (gemini-2.0-flash)
    @Value("${gemini.model:gemini-2.0-flash}")
    private String model;

    // ------------------------------------------------------------------ constants

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

    // FIX 2: finish reasons that carry usable content
    // "OTHER" is returned by Gemini when grounding/search tools are used —
    // it does NOT mean an error; the response content is still valid.
    private static final Set<String> ACCEPTABLE_FINISH_REASONS =
            Set.of("STOP", "MAX_TOKENS", "OTHER");

    private static final List<String> TARGET_URLS = List.of(
            "https://www.freshersworld.com/jobs/freshers",
            "https://www.fresherslive.com/latest-jobs",
            "https://www.freshersnow.com/category/job-notifications/",
            "https://www.freshersvoice.com/category/jobs/it-jobs/",
            "https://dare2compete.com/opportunities/jobs",
            "https://unstop.com/jobs",
            "https://www.placementindia.com/fresher-jobs.htm",
            "https://nextstep.tcs.com/campus",
            "https://career.infosys.com/joblist",
            "https://careers.wipro.com/careers-home/jobs",
            "https://careers.cognizant.com/global/en/search-results",
            "https://www.hcltech.com/careers/students-and-freshers",
            "https://campus.accenture.com/in/en/students/pages/jobs.aspx",
            "https://www.monsterindia.com/freshers-jobs.html",
            "https://www.timesjobs.com/candidate/job-search.html?searchType=personalizedSearch&from=submit&txtKeywords=fresher&txtLocation=India",
            "https://www.wisdomjobs.com/e-university/freshers-jobs-in-india.html",
            "https://www.hirist.tech/jobs/fresher",
            "https://www.geeksforgeeks.org/jobs/",
            "https://www.sarkariresult.com/latestjob/",
            "https://www.freejobalert.com/latest-notifications/",
            "https://www.rojgarresult.com/"
    );

    // Free tier limit: 5 requests per minute.
    // 15 seconds between calls keeps us safely under that limit.
    private static final long DELAY_BETWEEN_CALLS_MS = 15_000;

    // ------------------------------------------------------------------ infrastructure

    private final RestClient restClient = RestClient.create(
            "https://generativelanguage.googleapis.com"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ------------------------------------------------------------------ public API

    /**
     * Uses Gemini's built-in Google Search grounding to discover fresh
     * fresher/campus-drive listings across the web.
     *
     * FIX 3: tool name changed from "google_search" (snake_case, old/wrong)
     *         to "googleSearch" (camelCase, correct Gemini v1beta name).
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
                // FIX 3: "googleSearch" is the correct camelCase tool name
                "tools", List.of(
                        Map.of("googleSearch", Map.of())
                )
        );

        log.info("Running open-ended AI_SEARCH discovery (model={})...", model);
        return callGemini(body);
    }

    /**
     * Iterates through TARGET_URLS and asks Gemini to read each page
     * via the url_context tool, extracting fresher listings.
     */
    public List<ScrapedDriveDTO> discoverFromUrls() {
        List<ScrapedDriveDTO> allDrives = new ArrayList<>();
        for (int i = 0; i < TARGET_URLS.size(); i++) {
            String url = TARGET_URLS.get(i);
            log.info("Checking URL ({}/{}): {}", i + 1, TARGET_URLS.size(), url);

            List<ScrapedDriveDTO> results = extractListingsFromUrl(url);
            log.info("Found {} listing(s) from {}", results.size(), url);
            allDrives.addAll(results);

            // Rate limit: free tier allows 5 requests/min.
            // Skip delay after the last URL.
            if (i < TARGET_URLS.size() - 1) {
                try {
                    log.debug("Waiting {}ms before next Gemini call...", DELAY_BETWEEN_CALLS_MS);
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
            // FIX 4: log the full cause so you can see the actual Gemini error
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

        // FIX 5: log top-level error block if present (e.g. invalid tool name,
        //         quota exceeded, bad API key) — previously this was silently swallowed.
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

            // FIX 2: "OTHER" is normal when grounding tools are active
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

            // Concatenate only text parts; skip tool-use / grounding metadata parts
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

            // Strip optional markdown fences the model sometimes adds despite instructions
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

    /**
     * Removes ```json ... ``` or ``` ... ``` fences that the model sometimes
     * wraps around its output despite being told not to.
     */
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