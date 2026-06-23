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

    private static final List<String> RSS_FEEDS = List.of(
        // General fresher job boards
        "https://www.freejobalert.com/feed/",
        "https://www.freshersworld.com/feed",
        "https://www.sarkariresult.com/feed/",
        "https://www.rojgarresult.com/feed/",
        "https://www.freshersnow.com/feed/",

        // B.E/B.Tech & campus focused
        "https://dare2compete.com/feed",
        "https://www.instahyre.com/blog/feed/",
        "https://jobsforher.com/feed/",
        "https://www.naukri.com/blog/feed/",
        "https://campus.naukri.com/feed/",

        // BPO / Call centre focused
        "https://www.timesjobs.com/candidate/job-search.html?sequence=0&startPage=0&txtKeywords=bpo&rss=1",
        "https://www.shine.com/job-search/bpo-jobs/rss/",
        "https://www.quikr.com/jobs/bpo-jobs+India/rss",

        // Core engineering & PSU focused
        "https://www.bhel.com/rss/careers.xml",
        "https://www.ntpc.co.in/rss/careers",
        "https://www.engineeringcareers.in/feed/",
        "https://iimjobs.com/feed/",
        "https://www.psualert.in/feed/",
        "https://www.oilgas.in/feed/",
        "https://www.placementindia.com/rss/core-jobs"
    );

    // ── Keyword Lists ──────────────────────────────────────────────────────

    private static final List<String> FRESHER_KEYWORDS = List.of(
        "fresher", "fresh graduate", "entry level", "0-2 years",
        "2024 batch", "2025 batch", "campus", "graduate trainee"
    );

    private static final List<String> IT_KEYWORDS = List.of(
        "software", "developer", "engineer", "java", "python",
        "data analyst", "data engineer", "it ", "tech", "coding", "programmer"
    );

    private static final List<String> BANKING_KEYWORDS = List.of(
        "bank", "ibps", "sbi", "rbi", "finance", "insurance", "nbfc"
    );

    private static final List<String> GOVT_KEYWORDS = List.of(
        "sarkari", "government", "psu", "upsc", "ssc", "railway",
        "recruitment", "notification", "vacancy"
    );

    private static final List<String> INTERNSHIP_KEYWORDS = List.of(
        "internship", "intern "  // trailing space avoids matching "internal"
    );

    private static final List<String> BTECH_KEYWORDS = List.of(
        "b.e", "b.tech", "be ", "btech", "bachelor of engineering",
        "bachelor of technology", "engineering graduate", "engineering fresher",
        "cse", "ece", "mechanical", "electrical", "civil engineering",
        "it graduate", "computer science",
        "hackathon", "campus drive", "placement", "on-campus", "off-campus"
    );

    private static final List<String> ARTS_KEYWORDS = List.of(
        "b.a", "ba ", "arts graduate", "bachelor of arts",
        "bba", "b.b.a", "bsc", "b.sc", "bachelor of science",
        "humanities", "social science", "commerce graduate",
        "b.com", "bcom", "economics", "english graduate", "history",
        "mba fresher", "pgdm", "mass communication", "journalism"
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
        // Electrical & Power
        "power systems", "power electronics", "power generation", "power distribution",
        "transmission", "substation", "switchgear", "transformer", "hvdc", "scada",
        "plc", "dcs", "automation", "electrical engineering", "high voltage",
        // Mechanical
        "mechanical engineering", "manufacturing", "production engineering",
        "design engineer", "product design", "cad", "cam", "solidworks", "ansys",
        "finite element", "fea", "hvac", "thermal", "fluid mechanics",
        "tool design", "die casting", "injection moulding", "cnc",
        // Electronics & Instrumentation
        "electronics", "instrumentation", "embedded systems", "vlsi", "pcb design",
        "signal processing", "control systems", "pid", "iot hardware",
        // Civil & Structural
        "civil engineering", "structural engineering", "construction", "infrastructure",
        "geotechnical", "surveying",
        // Aerospace & Defence
        "aerospace", "aeronautical", "avionics", "propulsion", "defence",
        // Chemical & Process
        "chemical engineering", "process engineering", "refinery", "petrochemical",
        "polymer", "paint technology"
    );

    private static final List<String> CORE_COMPANIES = List.of(
        // Power & Energy
        "hitachi", "abb", "siemens", "schneider electric", "alstom", "bhel",
        "crompton", "havells", "legrand", "eaton", "emerson", "honeywell",
        "ge power", "general electric", "ge renewable", "ge grid",
        "power grid corporation", "pgcil", "ntpc", "nhpc", "neepco",
        "tata power", "adani power", "torrent power", "cesc", "jsw energy",
        "suzlon", "inox wind", "greenko", "renew power",
        // Industrial & Manufacturing
        "godrej", "godrej & boyce", "larsen toubro", "l&t", "thermax",
        "cummins", "atlas copco", "ingersoll rand", "kaeser",
        "kirloskar", "bharat forge", "tata motors",
        "ashok leyland", "bajaj auto", "hero motocorp", "tvs motor",
        "bosch", "continental", "valeo", "minda", "motherson",
        // Steel & Heavy Engineering
        "tata steel", "jsw steel", "sail", "jindal steel", "essar steel",
        "hindalco", "vedanta", "sterlite",
        // Oil & Gas / Process
        "iocl", "bpcl", "hpcl", "ongc", "gail", "oil india",
        "reliance industries", "essar oil", "shell india",
        // Aerospace & Defence
        "hal", "drdo", "isro", "bel", "beml", "midhani",
        "dynamatic technologies", "moog", "safran", "honeywell aerospace",
        // Automation & Instrumentation
        "rockwell automation", "yokogawa", "endress hauser", "krohne",
        "phoenix contact", "wago", "beckhoff", "delta electronics",
        "auma", "rotork",
        // Chemical / Process
        "pidilite", "asian paints", "berger paints", "srf", "atul ltd",
        "deepak nitrite", "navin fluorine", "aarti industries"
    );

    private static final List<String> KNOWN_ORGS = List.of(
        // IT
        "TCS", "Infosys", "Wipro", "HCL", "Accenture", "Cognizant",
        "IBM", "Capgemini", "Tech Mahindra",
        // Govt
        "IBPS", "SBI", "RBI", "UPSC", "SSC", "ISRO", "DRDO",
        "NTPC", "ONGC", "HAL", "Indian Railways", "Indian Army",
        "Indian Navy", "Indian Air Force",
        // Power & Energy
        "Hitachi", "ABB", "Siemens", "Schneider Electric", "Alstom",
        "BHEL", "Crompton", "Havells", "Eaton", "Emerson", "Honeywell",
        "GE Power", "General Electric", "PGCIL", "NHPC", "Tata Power",
        "Adani Power", "Torrent Power", "Suzlon",
        // Industrial & Manufacturing
        "Godrej", "L&T", "Larsen & Toubro", "Thermax", "Cummins",
        "Atlas Copco", "Kirloskar", "Bharat Forge", "Tata Motors",
        "Ashok Leyland", "Bosch", "Valeo", "Motherson",
        // Steel & Heavy
        "Tata Steel", "JSW Steel", "SAIL", "Jindal Steel", "Hindalco", "Vedanta",
        // Oil & Gas
        "IOCL", "BPCL", "HPCL", "GAIL", "Oil India", "Reliance Industries",
        // Aerospace & Defence
        "BEL", "BEML", "Dynamatic Technologies", "Safran", "Moog",
        // Automation
        "Rockwell Automation", "Yokogawa", "Endress Hauser", "Beckhoff",
        "Delta Electronics", "Phoenix Contact",
        // Chemical / Process
        "Pidilite", "Asian Paints", "Berger Paints", "SRF", "Deepak Nitrite"
    );

    private static final List<String> INDIAN_CITIES = List.of(
        "mumbai", "delhi", "bangalore", "bengaluru", "hyderabad",
        "chennai", "kolkata", "pune", "ahmedabad", "noida",
        "gurgaon", "gurugram", "remote", "pan india", "all india"
    );

    // Formats accepted when normalizing a free-text date found via regex
    // into the "YYYY-MM-DD" shape that ScrapedDriveDTO/DriveIngestionService expect.
    private static final List<DateTimeFormatter> FALLBACK_DATE_FORMATS = List.of(
        DateTimeFormatter.ISO_LOCAL_DATE,                            // 2026-06-23
        DateTimeFormatter.ofPattern("d/M/yyyy"),                     // 23/6/2026
        DateTimeFormatter.ofPattern("d-M-yyyy"),                     // 23-6-2026
        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),  // 23 June 2026
        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)    // 23 Jun 2026
    );

    // ── Public Entry Point ─────────────────────────────────────────────────

    public List<ScrapedDriveDTO> discoverFromRssFeeds() {
        Map<String, ScrapedDriveDTO> seen = new LinkedHashMap<>();
        // Entries with no link can't be deduped by URL — keep them all rather
        // than letting them collide under a single "null" key.
        List<ScrapedDriveDTO> noLinkEntries = new ArrayList<>();

        for (String feedUrl : RSS_FEEDS) {
            try {
                log.info("Fetching RSS feed: {}", feedUrl);
                List<ScrapedDriveDTO> results = parseFeed(feedUrl);
                log.info("Found {} listing(s) from {}", results.size(), feedUrl);
                for (ScrapedDriveDTO dto : results) {
                    String sourceUrl = dto.sourceUrl(); // record accessor, not getSourceUrl()
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

        String description  = extractDescription(entry);
        String combinedText = (title + " " + description).toLowerCase();

        // Degree / domain gates
        boolean isBtechRelated    = BTECH_KEYWORDS.stream().anyMatch(combinedText::contains);
        boolean isArtsRelated     = ARTS_KEYWORDS.stream().anyMatch(combinedText::contains);
        boolean isBpoRelated      = BPO_KEYWORDS.stream().anyMatch(combinedText::contains)
                                 || NON_VOICE_KEYWORDS.stream().anyMatch(combinedText::contains)
                                 || VOICE_KEYWORDS.stream().anyMatch(combinedText::contains);
        boolean isCoreEngineering = CORE_ENGINEERING_DOMAINS.stream().anyMatch(combinedText::contains)
                                 || CORE_COMPANIES.stream().anyMatch(combinedText::contains);

        // Must match at least one domain
        if (!isBtechRelated && !isArtsRelated && !isBpoRelated && !isCoreEngineering) return null;

        // Must also be job-relevant
        boolean relevant = FRESHER_KEYWORDS.stream().anyMatch(combinedText::contains)
            || GOVT_KEYWORDS.stream().anyMatch(combinedText::contains)
            || IT_KEYWORDS.stream().anyMatch(combinedText::contains)
            || BANKING_KEYWORDS.stream().anyMatch(combinedText::contains)
            || INTERNSHIP_KEYWORDS.stream().anyMatch(combinedText::contains)
            || BPO_KEYWORDS.stream().anyMatch(combinedText::contains)
            || NON_VOICE_KEYWORDS.stream().anyMatch(combinedText::contains)
            || VOICE_KEYWORDS.stream().anyMatch(combinedText::contains)
            || isCoreEngineering;

        if (!relevant) return null;

        String company     = extractCompany(title, description);
        String role        = cleanRole(title);
        String location    = extractLocation(combinedText);
        String sourceUrl   = entry.getLink();
        String category    = classifyCategory(combinedText, isBtechRelated, isArtsRelated, isBpoRelated, isCoreEngineering);
        String deadline    = extractDeadline(entry, combinedText);
        String desc        = buildDescription(title, description);
        String eligibility = extractEligibility(isBtechRelated, isArtsRelated, isBpoRelated, isCoreEngineering);

        return new ScrapedDriveDTO(company, role, location, deadline, sourceUrl, desc, category, eligibility);
    }

    // ── Field Extractors ───────────────────────────────────────────────────

    private String extractDescription(SyndEntry entry) {
        if (entry.getDescription() != null && entry.getDescription().getValue() != null) {
            return entry.getDescription().getValue()
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&[a-z]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
        }
        return "";
    }

    private String buildDescription(String title, String description) {
        if (description.isBlank()) return title.trim();

        String candidate = description.length() > 400
            ? description.substring(0, 400)
            : description;

        int lastPeriod = candidate.lastIndexOf('.');
        if (lastPeriod > 80) {
            candidate = candidate.substring(0, lastPeriod + 1);
        }
        return candidate.trim();
    }

    private String extractCompany(String title, String description) {
        // 1. Known org list (case-insensitive)
        String combined = title + " " + description;
        for (String org : KNOWN_ORGS) {
            if (combined.toLowerCase().contains(org.toLowerCase())) {
                return org;
            }
        }

        // 2. Title pattern: "<Company> Recruitment/Hiring/..."
        Pattern p = Pattern.compile(
            "^(.+?)\\s+(?:recruitment|hiring|vacancy|notification|careers|jobs)\\b",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = p.matcher(title.trim());
        if (m.find()) {
            String candidate = m.group(1).trim();
            if (candidate.split("\\s+").length <= 4) {
                return candidate;
            }
        }

        // 3. First 2-3 words as last resort
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

    private String classifyCategory(String text, boolean isBtech,
                                    boolean isArts, boolean isBpo,
                                    boolean isCoreEng) {
        if (INTERNSHIP_KEYWORDS.stream().anyMatch(text::contains)) return "INTERNSHIP";

        if (isBpo) {
            boolean isIntlVoice = text.contains("international voice")
                               || text.contains("us shift") || text.contains("uk shift")
                               || text.contains("night shift");
            if (isIntlVoice)                                                  return "BPO_INTERNATIONAL_VOICE";
            if (NON_VOICE_KEYWORDS.stream().anyMatch(text::contains))         return "BPO_NON_VOICE";
            if (VOICE_KEYWORDS.stream().anyMatch(text::contains))             return "BPO_DOMESTIC_VOICE";
            return "BPO_GENERAL";
        }

        if (isCoreEng) {
            if (text.contains("power") || text.contains("electrical")
                    || text.contains("substation") || text.contains("transformer")
                    || text.contains("scada") || text.contains("plc"))        return "CORE_POWER_ELECTRICAL";

            if (text.contains("mechanical") || text.contains("manufacturing")
                    || text.contains("production") || text.contains("cad")
                    || text.contains("cnc") || text.contains("thermal"))      return "CORE_MECHANICAL";

            if (text.contains("oil") || text.contains("gas")
                    || text.contains("refinery") || text.contains("chemical")
                    || text.contains("process engineering"))                   return "CORE_OIL_GAS_CHEMICAL";

            if (text.contains("aerospace") || text.contains("aeronautical")
                    || text.contains("defence") || text.contains("avionics")) return "CORE_AEROSPACE_DEFENCE";

            if (text.contains("civil") || text.contains("structural")
                    || text.contains("construction"))                          return "CORE_CIVIL";

            if (text.contains("electronics") || text.contains("vlsi")
                    || text.contains("embedded") || text.contains("pcb"))     return "CORE_ELECTRONICS";

            return "CORE_ENGINEERING_GENERAL";
        }

        if (isBtech) {
            if (IT_KEYWORDS.stream().anyMatch(text::contains))   return "IT_SOFTWARE";
            if (GOVT_KEYWORDS.stream().anyMatch(text::contains)) return "GOVERNMENT";
            return "BE_BTECH";
        }

        if (isArts) {
            if (BANKING_KEYWORDS.stream().anyMatch(text::contains)) return "BANKING";
            if (GOVT_KEYWORDS.stream().anyMatch(text::contains))    return "GOVERNMENT";
            return "ARTS";
        }

        return "OTHERS";
    }

    private String extractEligibility(boolean isBtech, boolean isArts,
                                      boolean isBpo, boolean isCoreEng) {
        if (isBpo)                 return "Any Graduate";
        if (isCoreEng && isBtech)  return "B.E/B.Tech (Core Engineering)";
        if (isCoreEng)             return "B.E/B.Tech / Diploma (Core Engineering)";
        if (isBtech && isArts)     return "B.E/B.Tech, Arts/Science/Commerce";
        if (isBtech)               return "B.E/B.Tech";
        if (isArts)                return "Arts/Science/Commerce";
        return "Any Graduate";
    }

    private String extractDeadline(SyndEntry entry, String text) {
        // 1. Use RSS published date + 30 days as a proxy.
        //    Always emit ISO "YYYY-MM-DD" — DriveIngestionService parses this
        //    directly via LocalDate.parse, with no other format accepted.
        if (entry.getPublishedDate() != null) {
            return entry.getPublishedDate()
                .toInstant()
                .atZone(ZoneId.of("Asia/Kolkata"))
                .toLocalDate()
                .plusDays(30)
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        // 2. Regex fallback on text body — the match can come back in any of
        //    several human formats, so normalize before returning it.
        Pattern datePattern = Pattern.compile(
            "(\\d{1,2}[/-]\\d{1,2}[/-]\\d{4})|"  +
            "(\\d{4}-\\d{2}-\\d{2})|"             +
            "(\\d{1,2}\\s+(?:january|february|march|april|may|june|july|" +
            "august|september|october|november|december)\\s+\\d{4})",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = datePattern.matcher(text);
        if (!matcher.find()) return null;

        return normalizeToIso(matcher.group().trim());
    }

    /**
     * Parses a free-text date found via regex into "YYYY-MM-DD".
     * Tries each known input shape in turn; returns null (rather than a
     * malformed string) if none match, since a bad string would otherwise
     * blow up LocalDate.parse() downstream in DriveIngestionService.
     */
    private String normalizeToIso(String raw) {
        for (DateTimeFormatter fmt : FALLBACK_DATE_FORMATS) {
            try {
                return LocalDate.parse(raw, fmt).format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // try next candidate format
            }
        }
        log.warn("Could not normalize deadline date '{}' to ISO format; dropping it.", raw);
        return null;
    }
}