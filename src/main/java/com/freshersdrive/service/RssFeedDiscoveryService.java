package com.freshersdrive.service;

import com.freshersdrive.dto.ScrapedDriveDTO;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.*;

@Slf4j
@Service
public class RssFeedDiscoveryService {

    // ── RSS Feed Sources ───────────────────────────────────────────────────
    // REMOVED: mbacrystalball, pagalguy, mbauniverse (pure blog feeds — no job listings)
    // REMOVED: iimjobs (blog-heavy, unreliable RSS)
    // REMOVED: timesjobs/shine BPO RSS (broken/redirect URLs)
    // REMOVED: bhel/ntpc (dead XML endpoints)
    private static final List<String> RSS_FEEDS = List.of(
        // Fresher / campus general
        "https://www.freshersworld.com/feed",
        "https://www.freshersnow.com/feed/",
        "https://dare2compete.com/feed",
        "https://campus.naukri.com/feed/",

        // Govt / sarkari
        "https://www.freejobalert.com/feed/",
        "https://www.psualert.in/feed/",
        "https://www.rojgarresult.com/feed/",

        // Core engineering / PSU
        "https://www.placementindia.com/rss/core-jobs",
        "https://www.engineeringcareers.in/feed/",
        "https://www.oilgas.in/feed/"
    );

    // ── Exclusion Keywords ──────────────────────────────────────────────────
    private static final List<String> EXCLUSION_KEYWORDS = List.of(
        "answer key", "answer sheet", "admit card", "hall ticket",
        "exam result", "result declared", "result out", "merit list",
        "cut off", "cutoff marks", "syllabus", "exam pattern",
        "exam date", "exam schedule", "previous year papers",
        "model papers", "sample papers", "mock test", "scorecard",
        "score card", "rank card", "interview call letter",
        "exam postponed", "exam cancelled", "revised exam date",
        "question paper", "exam analysis"
    );

    // ── Gate 1.5: Job posting signal keywords ─────────────────────────────
    // A post MUST contain at least one of these to pass — this drops
    // blog articles, guides, news stories, and MBA comparison posts
    // that slip past the exclusion gate.
    private static final List<String> JOB_SIGNAL_KEYWORDS = List.of(
        "apply", "vacancy", "vacancies", "recruitment", "hiring", "opening",
        "position", "recruit", "notification", "drive", "career", "job",
        "post", "walk-in", "walkin", "registration open",
        "applications invited", "applications are invited",
        "last date to apply", "last date", "apply online", "apply now",
        "job opening", "job vacancy", "fresher hiring", "campus drive",
        "off campus", "off-campus"
    );

    // ── Degree Keyword Lists ───────────────────────────────────────────────

    private static final List<String> FRESHER_KEYWORDS = List.of(
        "fresher", "fresh graduate", "entry level", "0-2 years",
        "2024 batch", "2025 batch", "2026 batch", "campus", "graduate trainee"
    );

    private static final List<String> IT_KEYWORDS = List.of(
        "software", "developer", "engineer", "java", "python",
        "data analyst", "data engineer", "it", "tech", "coding", "programmer"
    );

    private static final List<String> BANKING_KEYWORDS = List.of(
        "bank", "ibps", "sbi", "rbi", "finance", "insurance", "nbfc"
    );

    private static final List<String> GOVT_KEYWORDS = List.of(
        "sarkari", "government", "psu", "upsc", "ssc", "railway",
        "recruitment", "notification", "vacancy"
    );

    private static final List<String> INTERNSHIP_KEYWORDS = List.of(
        "internship", "intern"
    );

    private static final List<String> BTECH_KEYWORDS = List.of(
        "b.e", "b.tech", "be", "btech", "bachelor of engineering",
        "bachelor of technology", "engineering graduate", "engineering fresher",
        "cse", "ece", "mechanical", "electrical", "civil engineering",
        "it graduate", "computer science",
        "hackathon", "campus drive", "placement", "on-campus", "off-campus"
    );

    private static final List<String> ARTS_KEYWORDS = List.of(
        "b.a", "ba", "arts graduate", "bachelor of arts",
        "bba", "b.b.a", "bsc", "b.sc", "bachelor of science",
        "humanities", "social science", "commerce graduate",
        "b.com", "bcom", "economics", "english graduate", "history",
        "mba fresher", "pgdm", "mass communication", "journalism"
    );

    // ── MBA keyword list ──────────────────────────────────────────────────
    // NOTE: deliberately narrow — only phrases that appear in *job postings*,
    // not in blog articles about MBA programs. "mba" alone is intentionally
    // excluded because it fires on every MBA guide/article. Use compound
    // phrases that only job ads use.
    private static final List<String> MBA_KEYWORDS = List.of(
        "mba fresher", "mba passout", "mba 2024", "mba 2025", "mba 2026",
        "pgdm fresher", "pgdm passout",
        "post graduate diploma in management",
        "master of business administration",
        "management trainee", "management graduate",
        "finance graduate", "marketing graduate", "hr graduate",
        "operations management graduate", "supply chain graduate",
        "associate consultant", "consultant trainee",
        "mba hiring", "mba recruitment", "mba vacancy",
        "mba off campus", "mba campus drive"
    );

    private static final List<String> ME_MTECH_KEYWORDS = List.of(
        "m.e", "m.tech", "me", "mtech",
        "master of engineering", "master of technology",
        "m.e.", "m.tech.",
        "postgraduate engineer", "pg engineer", "research engineer",
        "r&d engineer", "research and development engineer",
        "gate qualified", "gate score",
        "project engineer", "senior engineer fresher"
    );

    private static final List<String> BPO_KEYWORDS = List.of(
        "bpo", "kpo", "lpo", "ites", "business process",
        "sutherland", "concentrix", "wipro bpo", "infosys bps",
        "tcs bps", "accenture bpo", "mphasis", "teleperformance",
        "firstsource", "hinduja", "wns", "startek", "convergys",
        "hexaware", "conneqt", "igate", "serco", "aegis"
    );

    private static final List<String> NON_VOICE_KEYWORDS = List.of(
        "non voice", "non-voice", "back office", "data entry",
        "chat support", "email support", "ticket handling",
        "content moderation", "document processing", "data processing",
        "form processing", "claims processing", "accounts processing",
        "medical coding", "medical billing", "annotation", "tagging",
        "transaction processing", "order processing"
    );

    private static final List<String> VOICE_KEYWORDS = List.of(
        "voice process", "international voice", "domestic voice",
        "call center", "call centre", "customer support", "customer service",
        "customer care", "inbound", "outbound", "telecalling",
        "technical support", "helpdesk", "service desk",
        "us shift", "uk shift", "night shift", "rotational shift",
        "blended process", "semi voice"
    );

    private static final List<String> CORE_ENGINEERING_DOMAINS = List.of(
        "power systems", "power electronics", "power generation", "power distribution",
        "transmission", "substation", "switchgear", "transformer", "hvdc", "scada",
        "plc", "dcs", "automation", "electrical engineering", "high voltage",
        "mechanical engineering", "manufacturing", "production engineering",
        "design engineer", "product design", "cad", "cam", "solidworks", "ansys",
        "finite element", "fea", "hvac", "thermal", "fluid mechanics",
        "tool design", "die casting", "injection moulding", "cnc",
        "electronics", "instrumentation", "embedded systems", "vlsi", "pcb design",
        "signal processing", "control systems", "pid", "iot hardware",
        "civil engineering", "structural engineering", "construction", "infrastructure",
        "geotechnical", "surveying",
        "aerospace", "aeronautical", "avionics", "propulsion", "defence",
        "chemical engineering", "process engineering", "refinery", "petrochemical",
        "polymer", "paint technology"
    );

    private static final List<String> CORE_COMPANIES = List.of(
        "hitachi", "abb", "siemens", "schneider electric", "alstom", "bhel",
        "crompton", "havells", "legrand", "eaton", "emerson", "honeywell",
        "ge power", "general electric", "ge renewable", "ge grid",
        "power grid corporation", "pgcil", "ntpc", "nhpc", "neepco",
        "tata power", "adani power", "torrent power", "cesc", "jsw energy",
        "suzlon", "inox wind", "greenko", "renew power",
        "godrej", "godrej & boyce", "larsen toubro", "l&t", "thermax",
        "cummins", "atlas copco", "ingersoll rand", "kaeser",
        "kirloskar", "bharat forge", "tata motors",
        "ashok leyland", "bajaj auto", "hero motocorp", "tvs motor",
        "bosch", "continental", "valeo", "minda", "motherson",
        "tata steel", "jsw steel", "sail", "jindal steel", "essar steel",
        "hindalco", "vedanta", "sterlite",
        "iocl", "bpcl", "hpcl", "ongc", "gail", "oil india",
        "reliance industries", "essar oil", "shell india",
        "hal", "drdo", "isro", "bel", "beml", "midhani",
        "dynamatic technologies", "moog", "safran", "honeywell aerospace",
        "rockwell automation", "yokogawa", "endress hauser", "krohne",
        "phoenix contact", "wago", "beckhoff", "delta electronics",
        "auma", "rotork",
        "pidilite", "asian paints", "berger paints", "srf", "atul ltd",
        "deepak nitrite", "navin fluorine", "aarti industries"
    );

    private static final List<String> KNOWN_ORGS = List.of(
        "TCS", "Infosys", "Wipro", "HCL", "Accenture", "Cognizant",
        "IBM", "Capgemini", "Tech Mahindra",
        "IBPS", "SBI", "RBI", "UPSC", "SSC", "ISRO", "DRDO",
        "NTPC", "ONGC", "HAL", "Indian Railways", "Indian Army",
        "Indian Navy", "Indian Air Force",
        "Hitachi", "ABB", "Siemens", "Schneider Electric", "Alstom",
        "BHEL", "Crompton", "Havells", "Eaton", "Emerson", "Honeywell",
        "GE Power", "General Electric", "PGCIL", "NHPC", "Tata Power",
        "Adani Power", "Torrent Power", "Suzlon",
        "Godrej", "L&T", "Larsen & Toubro", "Thermax", "Cummins",
        "Atlas Copco", "Kirloskar", "Bharat Forge", "Tata Motors",
        "Ashok Leyland", "Bosch", "Valeo", "Motherson",
        "Tata Steel", "JSW Steel", "SAIL", "Jindal Steel", "Hindalco", "Vedanta",
        "IOCL", "BPCL", "HPCL", "GAIL", "Oil India", "Reliance Industries",
        "BEL", "BEML", "Dynamatic Technologies", "Safran", "Moog",
        "Rockwell Automation", "Yokogawa", "Endress Hauser", "Beckhoff",
        "Delta Electronics", "Phoenix Contact",
        "Pidilite", "Asian Paints", "Berger Paints", "SRF", "Deepak Nitrite"
    );

    private static final List<String> INDIAN_CITIES = List.of(
        "mumbai", "delhi", "bangalore", "bengaluru", "hyderabad",
        "chennai", "kolkata", "pune", "ahmedabad", "noida",
        "gurgaon", "gurugram", "remote", "pan india", "all india"
    );

    private static final List<DateTimeFormatter> FALLBACK_DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("d-M-yyyy"),
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    );

    // ── Keyword Matching ───────────────────────────────────────────────────

    private static List<Pattern> toWordBoundaryPatterns(List<String> keywords) {
        List<Pattern> patterns = new ArrayList<>(keywords.size());
        for (String kw : keywords) {
            patterns.add(Pattern.compile(
                "\\b" + Pattern.quote(kw.trim()) + "\\b",
                Pattern.CASE_INSENSITIVE
            ));
        }
        return patterns;
    }

    private static boolean matchesAny(String text, List<Pattern> patterns) {
        for (Pattern p : patterns) {
            if (p.matcher(text).find()) return true;
        }
        return false;
    }

    private static final List<Pattern> EXCLUSION_PATTERNS        = toWordBoundaryPatterns(EXCLUSION_KEYWORDS);
    private static final List<Pattern> JOB_SIGNAL_PATTERNS       = toWordBoundaryPatterns(JOB_SIGNAL_KEYWORDS);
    private static final List<Pattern> FRESHER_PATTERNS          = toWordBoundaryPatterns(FRESHER_KEYWORDS);
    private static final List<Pattern> IT_PATTERNS               = toWordBoundaryPatterns(IT_KEYWORDS);
    private static final List<Pattern> BANKING_PATTERNS          = toWordBoundaryPatterns(BANKING_KEYWORDS);
    private static final List<Pattern> GOVT_PATTERNS             = toWordBoundaryPatterns(GOVT_KEYWORDS);
    private static final List<Pattern> INTERNSHIP_PATTERNS       = toWordBoundaryPatterns(INTERNSHIP_KEYWORDS);
    private static final List<Pattern> BTECH_PATTERNS            = toWordBoundaryPatterns(BTECH_KEYWORDS);
    private static final List<Pattern> ARTS_PATTERNS             = toWordBoundaryPatterns(ARTS_KEYWORDS);
    private static final List<Pattern> MBA_PATTERNS              = toWordBoundaryPatterns(MBA_KEYWORDS);
    private static final List<Pattern> ME_MTECH_PATTERNS         = toWordBoundaryPatterns(ME_MTECH_KEYWORDS);
    private static final List<Pattern> BPO_PATTERNS              = toWordBoundaryPatterns(BPO_KEYWORDS);
    private static final List<Pattern> NON_VOICE_PATTERNS        = toWordBoundaryPatterns(NON_VOICE_KEYWORDS);
    private static final List<Pattern> VOICE_PATTERNS            = toWordBoundaryPatterns(VOICE_KEYWORDS);
    private static final List<Pattern> CORE_ENGINEERING_PATTERNS = toWordBoundaryPatterns(CORE_ENGINEERING_DOMAINS);
    private static final List<Pattern> CORE_COMPANY_PATTERNS     = toWordBoundaryPatterns(CORE_COMPANIES);

    private static final Pattern DEADLINE_LABEL_PATTERN = Pattern.compile(
        "(?:last\\s+date|apply\\s+by|closing\\s+date|deadline|last\\s+date\\s+to\\s+apply)" +
        "\\s*[:\\-]?\\s*" +
        "(\\d{1,2}[\\s/\\-](?:\\d{1,2}|(?:jan(?:uary)?|feb(?:ruary)?|mar(?:ch)?|apr(?:il)?|may|" +
        "jun(?:e)?|jul(?:y)?|aug(?:ust)?|sep(?:tember)?|oct(?:ober)?|nov(?:ember)?|dec(?:ember)?))" +
        "[\\s/\\-]\\d{2,4})",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern OFFICIAL_LINK_PATTERN = Pattern.compile(
        "<a\\s[^>]*href=['\"]([^'\"]+)['\"][^>]*>\\s*(?:[^<]*(?:apply|official|notification|here|click)[^<]*)\\s*</a>",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern ANY_LINK_PATTERN = Pattern.compile(
        "<a\\s[^>]*href=['\"]([^'\"]+)['\"]",
        Pattern.CASE_INSENSITIVE
    );

    // ── Public Entry Point ─────────────────────────────────────────────────

    public List<ScrapedDriveDTO> discoverFromRssFeeds() {
        Map<String, ScrapedDriveDTO> seen = new LinkedHashMap<>();
        List<ScrapedDriveDTO> noLinkEntries = new ArrayList<>();

        for (String feedUrl : RSS_FEEDS) {
            try {
                log.info("Fetching RSS feed: {}", feedUrl);
                List<ScrapedDriveDTO> results = parseFeed(feedUrl);
                log.info("Found {} listing(s) from {}", results.size(), feedUrl);
                for (ScrapedDriveDTO dto : results) {
                    String sourceUrl = dto.sourceUrl();
                    if (sourceUrl == null || sourceUrl.isBlank()) {
                        noLinkEntries.add(dto);
                    } else {
                        seen.putIfAbsent(sourceUrl, dto);
                    }
                }
            } catch (Exception e) {
                log.error("Failed to fetch RSS feed {}: {}", feedUrl, e.getMessage());
            }
        }

        List<ScrapedDriveDTO> all = new ArrayList<>(seen.values());
        all.addAll(noLinkEntries);
        log.info("RSS discovery complete. Unique listings: {}", all.size());
        return all;
    }

    // ── Feed Parsing ───────────────────────────────────────────────────────

    private List<ScrapedDriveDTO> parseFeed(String feedUrl) throws Exception {
        List<ScrapedDriveDTO> drives = new ArrayList<>();

        URL url = new URL(feedUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(15000);
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; FreshersDriveBot/1.0)");
        connection.setRequestProperty("Accept", "application/rss+xml, application/xml, text/xml");

        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(connection.getInputStream()));

        for (SyndEntry entry : feed.getEntries()) {
            try {
                ScrapedDriveDTO dto = entryToDto(entry);
                if (dto != null) drives.add(dto);
            } catch (Exception e) {
                log.warn("Failed to parse RSS entry '{}': {}", entry.getTitle(), e.getMessage());
            }
        }
        return drives;
    }

    // ── Entry → DTO ────────────────────────────────────────────────────────

    private ScrapedDriveDTO entryToDto(SyndEntry entry) {
        String title = entry.getTitle();
        if (title == null || title.isBlank()) return null;

        String rawHtmlDescription = "";
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            rawHtmlDescription = entry.getDescription().getValue();
        }

        String description  = stripHtml(rawHtmlDescription);
        String combinedText = (title + " " + description).toLowerCase();

        // ── Gate 1: Hard exclusion (answer key, admit card, result…) ──────
        if (matchesAny(combinedText, EXCLUSION_PATTERNS)) {
            log.debug("Dropped (exclusion): {}", title);
            return null;
        }

        // ── Gate 1.5: Must look like an actual job posting ────────────────
        // Drops blog articles, MBA guides, news, and comparison posts that
        // slip through Gate 1 by containing MBA/BTECH keywords incidentally.
        if (!matchesAny(combinedText, JOB_SIGNAL_PATTERNS)) {
            log.debug("Dropped (not a job posting): {}", title);
            return null;
        }

        // ── Gate 2: Degree / domain classification ────────────────────────
        boolean isBtechRelated    = matchesAny(combinedText, BTECH_PATTERNS);
        boolean isArtsRelated     = matchesAny(combinedText, ARTS_PATTERNS);
        boolean isBpoRelated      = matchesAny(combinedText, BPO_PATTERNS)
                                 || matchesAny(combinedText, NON_VOICE_PATTERNS)
                                 || matchesAny(combinedText, VOICE_PATTERNS);
        boolean isCoreEngineering = matchesAny(combinedText, CORE_ENGINEERING_PATTERNS)
                                 || matchesAny(combinedText, CORE_COMPANY_PATTERNS);
        boolean isMbaRelated      = matchesAny(combinedText, MBA_PATTERNS);
        boolean isMeRelated       = matchesAny(combinedText, ME_MTECH_PATTERNS);

        if (!isBtechRelated && !isArtsRelated && !isBpoRelated
                && !isCoreEngineering && !isMbaRelated && !isMeRelated) {
            log.debug("Dropped (no degree/domain match): {}", title);
            return null;
        }

        // ── Gate 3: Relevance check ───────────────────────────────────────
        if (!isMbaRelated && !isMeRelated) {
            boolean relevant = matchesAny(combinedText, FRESHER_PATTERNS)
                || matchesAny(combinedText, GOVT_PATTERNS)
                || matchesAny(combinedText, IT_PATTERNS)
                || matchesAny(combinedText, BANKING_PATTERNS)
                || matchesAny(combinedText, INTERNSHIP_PATTERNS)
                || matchesAny(combinedText, BPO_PATTERNS)
                || matchesAny(combinedText, NON_VOICE_PATTERNS)
                || matchesAny(combinedText, VOICE_PATTERNS)
                || isCoreEngineering;
            if (!relevant) {
                log.debug("Dropped (not relevant): {}", title);
                return null;
            }
        }

        String company     = extractCompany(title, description);
        String role        = cleanRole(title);
        String location    = extractLocation(combinedText);
        String category    = classifyCategory(combinedText, isBtechRelated, isArtsRelated,
                                              isBpoRelated, isCoreEngineering,
                                              isMbaRelated, isMeRelated);
        String eligibility = extractEligibility(isBtechRelated, isArtsRelated, isBpoRelated,
                                                isCoreEngineering, isMbaRelated, isMeRelated);

        String deadline = extractDeadlineFromText(combinedText);
        if (deadline == null) {
            deadline = extractDeadlineFromPubDate(entry);
        }

        String officialLink = extractOfficialLink(rawHtmlDescription);
        String sourceUrl = (officialLink != null && !officialLink.isBlank())
            ? officialLink
            : entry.getLink();

        String desc = buildDescription(title, description);

        log.debug("Accepted: [{}] {} — {}", category, company, role);
        return new ScrapedDriveDTO(company, role, location, deadline, sourceUrl, desc, category, eligibility);
    }

    // ── Field Extractors ───────────────────────────────────────────────────

    private String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html
            .replaceAll("<[^>]+>", " ")
            .replaceAll("&[a-z]+;", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private String buildDescription(String title, String description) {
        if (description.isBlank()) return title.trim();
        String candidate = description.length() > 400
            ? description.substring(0, 400)
            : description;
        int lastPeriod = candidate.lastIndexOf('.');
        if (lastPeriod > 80) candidate = candidate.substring(0, lastPeriod + 1);
        return candidate.trim();
    }

    private String extractCompany(String title, String description) {
        String combined = title + " " + description;
        for (String org : KNOWN_ORGS) {
            if (combined.toLowerCase().contains(org.toLowerCase())) return org;
        }
        Pattern p = Pattern.compile(
            "^(.+?)\\s+(?:recruitment|hiring|vacancy|notification|careers|jobs)\\b",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(title.trim());
        if (m.find()) {
            String candidate = m.group(1).trim();
            if (candidate.split("\\s+").length <= 4) return candidate;
        }
        String[] words = title.trim().split("\\s+");
        int take = Math.min(3, words.length);
        return String.join(" ", Arrays.copyOfRange(words, 0, take));
    }

    private String cleanRole(String title) {
        return title
            .replaceAll("(?i)\\s*[|–-]\\s*apply (now|online|here).*$", "")
            .replaceAll("(?i)\\s*[|–-]\\s*(last date|deadline).*$", "")
            .replaceAll("(?i)\\s*[|–-]\\s*\\d{4}.*$", "")
            .trim();
    }

    private String extractLocation(String text) {
        for (String city : INDIAN_CITIES) {
            if (text.contains(city)) {
                String display = city.replace("pan india", "PAN India")
                                     .replace("all india", "PAN India");
                return Character.toUpperCase(display.charAt(0)) + display.substring(1);
            }
        }
        return "India";
    }

    private String extractDeadlineFromText(String text) {
        Matcher m = DEADLINE_LABEL_PATTERN.matcher(text);
        if (m.find()) {
            return normalizeToIso(m.group(1).trim());
        }
        return null;
    }

    private String extractDeadlineFromPubDate(SyndEntry entry) {
        if (entry.getPublishedDate() == null) return null;
        return entry.getPublishedDate()
            .toInstant()
            .atZone(ZoneId.of("Asia/Kolkata"))
            .toLocalDate()
            .plusDays(30)
            .format(DateTimeFormatter.ISO_LOCAL_DATE);
    }

    private String extractOfficialLink(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) return null;

        Matcher m = OFFICIAL_LINK_PATTERN.matcher(rawHtml);
        while (m.find()) {
            String href = m.group(1).trim();
            if (isExternalOfficialUrl(href)) return href;
        }

        Matcher any = ANY_LINK_PATTERN.matcher(rawHtml);
        while (any.find()) {
            String href = any.group(1).trim();
            if (isExternalOfficialUrl(href)) return href;
        }

        return null;
    }

    private boolean isExternalOfficialUrl(String url) {
        if (url == null || url.isBlank()) return false;
        if (!url.startsWith("http")) return false;
        if (url.startsWith("#") || url.startsWith("javascript")) return false;

        String[] aggregators = {
            "freejobalert.com", "freshersworld.com", "sarkariresult.com",
            "rojgarresult.com", "freshersnow.com", "naukri.com",
            "timesjobs.com", "shine.com", "psualert.in",
            "dare2compete.com", "instahyre.com", "iimjobs.com",
            "mbacrystalball.com", "pagalguy.com", "mbauniverse.com",
            "placementindia.com", "engineeringcareers.in", "oilgas.in"
        };
        for (String agg : aggregators) {
            if (url.contains(agg)) return false;
        }
        return true;
    }

    private String classifyCategory(String text, boolean isBtech, boolean isArts,
                                    boolean isBpo, boolean isCoreEng,
                                    boolean isMba, boolean isME) {
        if (matchesAny(text, INTERNSHIP_PATTERNS)) return "INTERNSHIP";

        if (isMba) {
            if (matchesAny(text, BANKING_PATTERNS)) return "MBA_FINANCE";
            if (text.contains("marketing"))         return "MBA_MARKETING";
            if (text.contains("human resource") || text.contains(" hr ")) return "MBA_HR";
            if (text.contains("operations") || text.contains("supply chain")) return "MBA_OPERATIONS";
            if (matchesAny(text, GOVT_PATTERNS))    return "MBA_GOVERNMENT";
            return "MBA_GENERAL";
        }

        if (isME) {
            if (isCoreEng) return "ME_CORE_ENGINEERING";
            if (matchesAny(text, IT_PATTERNS)) return "ME_IT";
            if (matchesAny(text, GOVT_PATTERNS)) return "ME_GOVERNMENT";
            return "ME_GENERAL";
        }

        if (isBpo) {
            boolean isIntlVoice = text.contains("international voice")
                               || text.contains("us shift") || text.contains("uk shift")
                               || text.contains("night shift");
            if (isIntlVoice)                          return "BPO_INTERNATIONAL_VOICE";
            if (matchesAny(text, NON_VOICE_PATTERNS)) return "BPO_NON_VOICE";
            if (matchesAny(text, VOICE_PATTERNS))     return "BPO_DOMESTIC_VOICE";
            return "BPO_GENERAL";
        }

        if (isCoreEng) {
            if (text.contains("power") || text.contains("electrical")
                    || text.contains("substation") || text.contains("transformer")
                    || text.contains("scada") || text.contains("plc"))   return "CORE_POWER_ELECTRICAL";
            if (text.contains("mechanical") || text.contains("manufacturing")
                    || text.contains("production") || text.contains("cad")
                    || text.contains("cnc") || text.contains("thermal")) return "CORE_MECHANICAL";
            if (text.contains("oil") || text.contains("gas")
                    || text.contains("refinery") || text.contains("chemical")
                    || text.contains("process engineering"))             return "CORE_OIL_GAS_CHEMICAL";
            if (text.contains("aerospace") || text.contains("aeronautical")
                    || text.contains("defence") || text.contains("avionics")) return "CORE_AEROSPACE_DEFENCE";
            if (text.contains("civil") || text.contains("structural")
                    || text.contains("construction"))                    return "CORE_CIVIL";
            if (text.contains("electronics") || text.contains("vlsi")
                    || text.contains("embedded") || text.contains("pcb")) return "CORE_ELECTRONICS";
            return "CORE_ENGINEERING_GENERAL";
        }

        if (isBtech) {
            if (matchesAny(text, IT_PATTERNS))   return "IT_SOFTWARE";
            if (matchesAny(text, GOVT_PATTERNS)) return "GOVERNMENT";
            return "BE_BTECH";
        }

        if (isArts) {
            if (matchesAny(text, BANKING_PATTERNS)) return "BANKING";
            if (matchesAny(text, GOVT_PATTERNS))    return "GOVERNMENT";
            return "ARTS";
        }

        return "OTHERS";
    }

    private String extractEligibility(boolean isBtech, boolean isArts, boolean isBpo,
                                      boolean isCoreEng, boolean isMba, boolean isME) {
        if (isMba && isME)        return "MBA / M.E / M.Tech";
        if (isMba)                return "MBA / PGDM";
        if (isME && isCoreEng)    return "M.E / M.Tech (Core Engineering)";
        if (isME)                 return "M.E / M.Tech";
        if (isBpo)                return "Any Graduate";
        if (isCoreEng && isBtech) return "B.E/B.Tech (Core Engineering)";
        if (isCoreEng)            return "B.E/B.Tech / Diploma (Core Engineering)";
        if (isBtech && isArts)    return "B.E/B.Tech, Arts/Science/Commerce";
        if (isBtech)              return "B.E/B.Tech";
        if (isArts)               return "Arts/Science/Commerce";
        return "Any Graduate";
    }

    private String normalizeToIso(String raw) {
        for (DateTimeFormatter fmt : FALLBACK_DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, fmt).format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        log.warn("Could not normalize deadline date '{}' to ISO format; dropping it.", raw);
        return null;
    }
}