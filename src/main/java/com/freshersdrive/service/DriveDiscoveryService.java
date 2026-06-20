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
                "description": "string, 2-3 sentences max"
              }
            ]

            If you find nothing, return an empty array [].
            """;

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
                "description": "string, 2-3 sentences max"
              }
            ]

            If there are no openings on the page, return [].
            """.formatted(url, url);

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

    @SuppressWarnings("unchecked")
    private List<ScrapedDriveDTO> parseGeminiResponse(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.get(0).get("text");

            String json = text.replaceAll("```json", "").replaceAll("```", "").trim();
            return objectMapper.readValue(json, new TypeReference<List<ScrapedDriveDTO>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse Gemini response as JSON", e);
            return List.of();
        } catch (Exception e) {
            log.error("Unexpected Gemini response shape: {}", response, e);
            return List.of();
        }
    }
}