package com.freshersdrive.service;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RssFeedDiscoveryService {

    // Free RSS feeds from fresher job sites
    private static final List<String> RSS_FEEDS = List.of(
        "https://www.freejobalert.com/feed/",
        "https://www.freshersworld.com/feed",
        "https://www.sarkariresult.com/feed/",
        "https://www.rojgarresult.com/feed/",
        "https://www.freshersnow.com/feed/",
        "https://www.employment-news.org/feed/"
    );

    // Keywords to identify fresher/entry-level jobs
    private static final List<String> FRESHER_KEYWORDS = List.of(
        "fresher", "fresh graduate", "entry level", "0-2 years",
        "2024 batch", "2025 batch", "campus", "trainee", "graduate"
    );

    // Keywords to identify government jobs
    private static final List<String> GOVT_KEYWORDS = List.of(
        "sarkari", "government", "psu", "upsc", "ssc", "railway",
        "bank", "ibps", "recruitment", "notification", "vacancy"
    );

    // Keywords to identify IT jobs
    private static final List<String> IT_KEYWORDS = List.of(
        "software", "developer", "engineer", "java", "python",
        "data", "analyst", "it ", "tech", "coding", "programmer"
    );

    // Keywords to identify banking jobs
    private static final List<String> BANKING_KEYWORDS = List.of(
        "bank", "ibps", "sbi", "rbi", "finance", "insurance", "nbfc"
    );

    // Keywords to identify internships
    private static final List<String> INTERNSHIP_KEYWORDS = List.of(
        "internship", "intern", "apprentice", "trainee"
    );

    public List<ScrapedDriveDTO> discoverFromRssFeeds() {
        List<ScrapedDriveDTO> allDrives = new ArrayList<>();

        for (String feedUrl : RSS_FEEDS) {
            try {
                log.info("Fetching RSS feed: {}", feedUrl);
                List<ScrapedDriveDTO> results = parseFeed(feedUrl);
                log.info("Found {} listing(s) from {}", results.size(), feedUrl);
                allDrives.addAll(results);
            } catch (Exception e) {
                log.error("Failed to fetch RSS feed {}: {}", feedUrl, e.getMessage());
            }
        }

        log.info("RSS discovery complete. Total: {} listing(s)", allDrives.size());
        return allDrives;
    }

    private List<ScrapedDriveDTO> parseFeed(String feedUrl) throws Exception {
        List<ScrapedDriveDTO> drives = new ArrayList<>();

        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(new URL(feedUrl)));

        for (SyndEntry entry : feed.getEntries()) {
            try {
                ScrapedDriveDTO dto = entryToDto(entry, feedUrl);
                if (dto != null) {
                    drives.add(dto);
                }
            } catch (Exception e) {
                log.warn("Failed to parse RSS entry '{}': {}", entry.getTitle(), e.getMessage());
            }
        }

        return drives;
    }

    private ScrapedDriveDTO entryToDto(SyndEntry entry, String feedUrl) {
        String title = entry.getTitle();
        if (title == null || title.isBlank()) return null;

        String titleLower = title.toLowerCase();
        String description = extractDescription(entry);
        String combinedText = (titleLower + " " + description).toLowerCase();

        // Filter: only include fresher/entry-level relevant posts
        boolean isFresherRelated = FRESHER_KEYWORDS.stream()
            .anyMatch(combinedText::contains);

        // For govt job sites, include all posts since they're all job-related
        boolean isGovtSite = feedUrl.contains("sarkari") ||
                             feedUrl.contains("rojgar") ||
                             feedUrl.contains("freejobalert") ||
                             feedUrl.contains("employment-news");

        if (!isFresherRelated && !isGovtSite) {
            return null;
        }

        String company = extractCompany(title, description);
        String role = title.trim();
        String location = extractLocation(combinedText);
        String sourceUrl = entry.getLink();
        String category = classifyCategory(combinedText);
        String deadline = extractDeadline(combinedText);
        String desc = description.isBlank()
            ? title
            : description.substring(0, Math.min(300, description.length()));

        return new ScrapedDriveDTO(
            company,
            role,
            location,
            deadline,
            sourceUrl,
            desc,
            category
        );
    }

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() != null &&
            entry.getDescription().getValue() != null) {
            // Strip HTML tags
            return entry.getDescription().getValue()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
        }
        return "";
    }

    private String extractCompany(String title, String description) {
        // Try to extract company from common patterns like "Company Name Recruitment 2025"
        // or "Company Name Hiring Freshers"
        String[] parts = title.split("(?i)\\s+(recruitment|hiring|jobs|vacancy|notification|careers)");
        if (parts.length > 0 && !parts[0].isBlank()) {
            return parts[0].trim();
        }
        return "Various Companies";
    }

    private String extractLocation(String text) {
        List<String> indianCities = List.of(
            "mumbai", "delhi", "bangalore", "bengaluru", "hyderabad",
            "chennai", "kolkata", "pune", "ahmedabad", "noida",
            "gurgaon", "gurugram", "india", "pan india", "remote"
        );

        for (String city : indianCities) {
            if (text.contains(city)) {
                return city.substring(0, 1).toUpperCase() + city.substring(1);
            }
        }
        return "PAN India";
    }

    private String classifyCategory(String text) {
        if (INTERNSHIP_KEYWORDS.stream().anyMatch(text::contains)) {
            return "INTERNSHIP";
        }
        if (IT_KEYWORDS.stream().anyMatch(text::contains)) {
            return "IT_SOFTWARE";
        }
        if (BANKING_KEYWORDS.stream().anyMatch(text::contains)) {
            return "BANKING";
        }
        if (GOVT_KEYWORDS.stream().anyMatch(text::contains)) {
            return "GOVERNMENT";
        }
        return "OTHERS";
    }

    private String extractDeadline(String text) {
        // Try to find dates like "31 December 2025", "2025-12-31", "31/12/2025"
        Pattern datePattern = Pattern.compile(
            "(\\d{4}-\\d{2}-\\d{2})|" +
            "(\\d{1,2}/\\d{1,2}/\\d{4})|" +
            "(\\d{1,2}\\s+(january|february|march|april|may|june|july|" +
            "august|september|october|november|december)\\s+\\d{4})",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = datePattern.matcher(text);
        if (matcher.find()) {
            return matcher.group().trim();
        }
        return null; // DriveIngestionService will use fallback of 30 days
    }
}