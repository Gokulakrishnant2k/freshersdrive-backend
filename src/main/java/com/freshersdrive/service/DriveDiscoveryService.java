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
 *                            of known career pages (configured below).
 *
 * Both return ScrapedDriveDTO lists; DriveIngestionService handles dedup + saving.
 */
@Slf4j
@Service
public class DriveDiscoveryService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model:gemini-2.5-flash}")
    private String model;

    // Keep in sync with com.freshersdrive.enums.JobCategory. Spelled out here
    // (rather than reflecting the enum) so the prompt text stays readable and
    // explicit about what each value means for the model.
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

    // Known career-page / job-board URLs to check every run.
    // TODO: move this to application.properties as a comma-separated list
    // if you want to edit it without redeploying.
    private static final List<String> TARGET_URLS = List.of(
        "https://careers.example-company.com/jobs"
        // add more known career pages here
    );

    private final RestClient restClient = RestClient.create(
        "https://generativelanguage.googleapis.com"
    );
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Open-ended discovery: search the web, then read promising pages
     * directly before extracting details.
     */
    public List<ScrapedDriveDTO> discoverNewDrives() {
        String prompt = """
            Search the web for campus placement / recruitment drives posted
            in the last 7 days for fresher / entry-level roles in India.
            For each promising result, open the actual page and read its
            full content before extracting details, so the listing reflects
            the real page rather than just a search snippet.

            Return ONLY a JSON array, no preamble, no markdown fences, in this exact shape:
            [
              {
                "company": "string",
                "role": "string",
                "location": "string",
                "applicationDeadline": "YYYY-MM-DD or null if unknown",
                "sourceUrl": "string",
                "description": "string, 2-3 sentences max",
                %s
              }
            ]

            If you find nothing, return an empty array [].
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

        return callGemini(body);
    }

    /**
     * Targeted discovery: read each known career page in TARGET_URLS
     * and extract its current listings. More reliable than open-ended
     * search since you control exactly which sites get checked.
     */
    public List<ScrapedDriveDTO> discoverFromUrls() {
        List<ScrapedDriveDTO> allDrives = new ArrayList<>();
        for (String url : TARGET_URLS) {
            allDrives.addAll(extractListingsFromUrl(url));
        }
        return allDrives;
    }

    private List<ScrapedDriveDTO> extractListingsFromUrl(String url) {
        String prompt = """
            Read the page at this URL: %s

            List every job / campus placement opening on this page for
            fresher / entry-level roles. Return ONLY a JSON array, no
            preamble, no markdown fences, in this exact shape:
            [
              {
                "company": "string",
                "role": "string",
                "location": "string",
                "applicationDeadline": "YYYY-MM-DD or null if unknown",
                "sourceUrl": "%s",
                "description": "string, 2-3 sentences max",
                %s
              }
            ]

            If there are no openings on the page, return [].
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
            log.error("Gemini discovery call failed", e);
            return List.of();
        }
    }

    /**
     * Pulls the model's text out of the response, then extracts the JSON
     * array from that text rather than assuming the text IS the array.
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
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                // No candidates usually means the prompt was blocked - check
                // response.promptFeedback.blockReason for the real cause.
                log.error("Gemini response had no candidates. Full response: {}", response);
                return List.of();
            }

            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            if (content == null) {
                // e.g. finishReason SAFETY / RECITATION / MAX_TOKENS with no content
                log.error("Gemini candidate had no content. Full candidate: {}", candidates.get(0));
                return List.of();
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            if (parts == null || parts.isEmpty()) {
                log.error("Gemini content had no parts. Full content: {}", content);
                return List.of();
            }

            text = (String) parts.get(0).get("text");
            if (text == null || text.isBlank()) {
                log.error("Gemini first part had no text. Full part: {}", parts.get(0));
                return List.of();
            }

            int start = text.indexOf('[');
            int end = text.lastIndexOf(']');
            if (start == -1 || end == -1 || end < start) {
                log.error("No JSON array found in Gemini response text: {}", text);
                return List.of();
            }

            String json = text.substring(start, end + 1);
            return objectMapper.readValue(json, new TypeReference<List<ScrapedDriveDTO>>() {});

        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini response as JSON. Raw text was: {}", text, e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected Gemini response shape: {}", response, e);
            return List.of();
        }
    }
}