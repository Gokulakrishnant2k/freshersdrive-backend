package com.freshersdrive.enums;

public enum DriveSource {
    MANUAL,           // added via AddDrive form
    AI_SEARCH,        // found via Gemini web search + url_context combined call
    AI_URL_CONTEXT    // found via a fixed, known career-page URL
}