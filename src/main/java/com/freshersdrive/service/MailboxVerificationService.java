package com.freshersdrive.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * Best-effort SMTP mailbox existence check.
 *
 * Connects to the recipient domain's MX server and issues RCPT TO
 * WITHOUT sending DATA — i.e. no email is actually sent. This lets us
 * ask "does this mailbox exist?" before we commit to a real send.
 *
 * IMPORTANT — this is inherently unreliable and must fail open:
 *   - Many providers (incl. Gmail at times) accept RCPT TO regardless
 *     and only bounce asynchronously after a real send.
 *   - Greylisting, rate limiting, and catch-all domains can produce
 *     false signals in both directions.
 *   - Outbound port 25 is blocked on many hosts/cloud providers, which
 *     will make every check time out.
 *
 * Result semantics:
 *   MailboxResult.EXISTS        -> server explicitly accepted the address
 *   MailboxResult.NOT_FOUND     -> server explicitly rejected with a
 *                                  permanent "no such user" style code
 *   MailboxResult.UNKNOWN       -> inconclusive (timeout, blocked port,
 *                                  server doesn't say) -> treat as OK
 */
@Service
@Slf4j
public class MailboxVerificationService {

    private static final int SMTP_PORT     = 25;
    private static final int CONNECT_TIMEOUT_MS = 4000;
    private static final int READ_TIMEOUT_MS    = 4000;

    // The "from" address used in the SMTP MAIL FROM probe.
    // Use your own verified sending domain here in production.
    private static final String PROBE_FROM = "verify@freshersdrive.com";

    public enum MailboxResult { EXISTS, NOT_FOUND, UNKNOWN }

    public MailboxResult checkMailboxExists(String email) {
        String domain = extractDomain(email);
        if (domain == null) return MailboxResult.UNKNOWN;

        List<String> mxHosts = resolveMxHosts(domain);
        if (mxHosts.isEmpty()) {
            // No MX record at all -> domain can't receive mail
            return MailboxResult.NOT_FOUND;
        }

        // Try MX hosts in order until one gives a conclusive answer
        for (String mxHost : mxHosts) {
            MailboxResult result = probeViaSmtp(mxHost, email);
            if (result != MailboxResult.UNKNOWN) {
                return result;
            }
        }
        return MailboxResult.UNKNOWN;
    }

    // ── DNS: resolve MX records, sorted by priority ────────────────────────────

    private List<String> resolveMxHosts(String domain) {
        List<String> hosts = new ArrayList<>();
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("dns:/" + domain, new String[]{"MX"});
            Attribute mx = attrs.get("MX");
            if (mx == null) return hosts;

            NamingEnumeration<?> records = mx.getAll();
            while (records.hasMore()) {
                // Format: "<priority> <host>."
                String record = records.next().toString().trim();
                String[] parts = record.split("\\s+");
                String host = parts[parts.length - 1];
                if (host.endsWith(".")) {
                    host = host.substring(0, host.length() - 1);
                }
                hosts.add(host);
            }
        } catch (Exception e) {
            log.warn("MX lookup failed for domain '{}': {}", domain, e.getMessage());
        }
        return hosts;
    }

    // ── SMTP probe: HELO / MAIL FROM / RCPT TO (no DATA sent) ──────────────────

    private MailboxResult probeViaSmtp(String mxHost, String email) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(mxHost, SMTP_PORT), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            BufferedReader in  = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            OutputStream out   = socket.getOutputStream();

            if (!expectCode(readResponse(in), "220")) return MailboxResult.UNKNOWN;

            send(out, "HELO freshersdrive.com\r\n");
            if (!expectCode(readResponse(in), "250")) return MailboxResult.UNKNOWN;

            send(out, "MAIL FROM:<" + PROBE_FROM + ">\r\n");
            if (!expectCode(readResponse(in), "250")) return MailboxResult.UNKNOWN;

            send(out, "RCPT TO:<" + email + ">\r\n");
            String rcptResponse = readResponse(in);

            send(out, "QUIT\r\n");

            if (rcptResponse == null) return MailboxResult.UNKNOWN;

            if (rcptResponse.startsWith("250") || rcptResponse.startsWith("251")) {
                return MailboxResult.EXISTS;
            }
            // Permanent failure codes that clearly mean "no such mailbox"
            if (rcptResponse.startsWith("550") || rcptResponse.startsWith("551")
                    || rcptResponse.startsWith("553")) {
                return MailboxResult.NOT_FOUND;
            }
            // 4xx (greylisting/temporary), 252 (can't verify but will try), etc.
            return MailboxResult.UNKNOWN;

        } catch (IOException e) {
            log.warn("SMTP probe to '{}' for '{}' failed/timed out: {}", mxHost, email, e.getMessage());
            return MailboxResult.UNKNOWN;
        }
    }

    private void send(OutputStream out, String line) throws IOException {
        out.write(line.getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private String readResponse(BufferedReader in) throws IOException {
        String line;
        String last = null;
        // Multi-line SMTP responses look like "250-..." then "250 ..." (space = last line)
        while ((line = in.readLine()) != null) {
            last = line;
            if (line.length() < 4 || line.charAt(3) == ' ') break;
        }
        return last;
    }

    private boolean expectCode(String response, String code) {
        return response != null && response.startsWith(code);
    }

    private String extractDomain(String email) {
        int at = email.indexOf('@');
        if (at < 0 || at == email.length() - 1) return null;
        return email.substring(at + 1).toLowerCase();
    }
}