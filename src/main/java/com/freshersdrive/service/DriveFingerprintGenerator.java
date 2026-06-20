package com.freshersdrive.service;

import org.springframework.stereotype.Component;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

@Component
public class DriveFingerprintGenerator {

    public String generate(String companyName, String jobRole, String location) {
        String normalized = normalize(companyName) + "|" + normalize(jobRole) + "|" + normalize(location);
        return DigestUtils.md5DigestAsHex(normalized.getBytes(StandardCharsets.UTF_8));
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase()
            .replaceAll("[^a-z0-9]", "")
            .trim();
    }
}