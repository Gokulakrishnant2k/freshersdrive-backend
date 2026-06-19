package com.freshersdrive.service;

import com.freshersdrive.entity.PasswordResetToken;
import com.freshersdrive.entity.User;
import com.freshersdrive.enums.Role;
import com.freshersdrive.repository.PasswordResetTokenRepository;
import com.freshersdrive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.naming.NamingEnumeration;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.LocalDateTime;
import java.util.Hashtable;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository               userRepository;
    private final PasswordEncoder              passwordEncoder;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService                 emailService;
    private final MailboxVerificationService   mailboxVerificationService;

    @Value("${app.password-reset.expiry-minutes:15}")
    private int expiryMinutes;

    // ── Register ──────────────────────────────────────────────────────────────

    @Transactional
    public String register(User user) {
        String email = user.getEmail().trim().toLowerCase();

        // 1. Check duplicate
        if (userRepository.existsByEmail(email)) {
            return "EMAIL_EXISTS";
        }

        // 2. Validate email format
        if (!email.matches("^[\\w.+\\-]+@[\\w.\\-]+\\.[a-z]{2,}$")) {
            return "INVALID_EMAIL";
        }

        // 2.5. Best-effort check that the mailbox actually exists.
        //      Fails OPEN: if the check is inconclusive (timeout, blocked
        //      port, server won't say) registration is still allowed.
        MailboxVerificationService.MailboxResult mailboxResult =
                mailboxVerificationService.checkMailboxExists(email);
        if (mailboxResult == MailboxVerificationService.MailboxResult.NOT_FOUND) {
            log.info("Registration rejected — mailbox does not exist: {}", email);
            return "EMAIL_NOT_FOUND";
        }

        // 3. Password strength check
        String password = user.getPassword();
        if (password == null || password.length() < 8
                || !password.matches(".*\\d.*")
                || !password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) {
            return "WEAK_PASSWORD";
        }

        // 4. Generate verification token
        String token = UUID.randomUUID().toString();

        // 5. Save user as unverified
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.ROLE_USER);
        user.setEmailVerified(false);        // ← fixed field name
        user.setVerificationToken(token);
        userRepository.save(user);

        // 6. Send verification email
        emailService.sendVerificationEmail(user, token);
        log.info("Verification email sent to: {}", email);

        return "OK";
    }

    // ── Verify Email ──────────────────────────────────────────────────────────

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepository.findByVerificationToken(token).orElse(null);

        if (user == null) {
            return "INVALID";
        }
        if (user.isEmailVerified()) {        // ← fixed field name
            return "ALREADY_VERIFIED";
        }

        user.setEmailVerified(true);         // ← fixed field name
        user.setVerificationToken(null);     // clear token after use
        userRepository.save(user);

        log.info("Email verified for: {}", user.getEmail());
        return "OK";
    }

    // ── Forgot Password ───────────────────────────────────────────────────────

    @Transactional
    public String inititateForgotPassword(String rawEmail) {
        String email = rawEmail.trim().toLowerCase();

        // 1. Basic format check
        if (!email.matches("^[\\w.+\\-]+@[\\w.\\-]+\\.[a-z]{2,}$")) {
            return "INVALID_FORMAT";
        }

        // 2. Must be a Google-managed domain
        if (!isGoogleManagedDomain(email)) {
            return "NOT_GOOGLE";
        }

        User user = userRepository.findByEmail(email).orElse(null);

        if (user != null) {
            // Invalidate any previous tokens for this email
            tokenRepository.deleteByEmail(email);

            // Create new token
            PasswordResetToken prt = new PasswordResetToken();
            prt.setToken(UUID.randomUUID().toString());
            prt.setEmail(email);
            prt.setExpiresAt(LocalDateTime.now().plusMinutes(expiryMinutes));
            prt.setUsed(false);
            tokenRepository.save(prt);

            // Send reset email
            emailService.sendPasswordResetEmail(user, prt.getToken());
            log.info("Password reset token created for: {}", email);
        }

        // Always return OK — prevents user-enumeration attacks
        return "OK";
    }

    // ── Validate Token ────────────────────────────────────────────────────────

    public boolean validateResetToken(String token) {
        return tokenRepository.findByToken(token)
                .map(t -> !t.isUsed() && t.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    // ── Reset Password ────────────────────────────────────────────────────────

    @Transactional
    public String resetPassword(String token, String newPassword) {
        PasswordResetToken prt = tokenRepository.findByToken(token).orElse(null);

        if (prt == null || prt.isUsed() || prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            return "INVALID";
        }

        if (newPassword == null || newPassword.length() < 8) {
            return "WEAK_PASSWORD";
        }

        User user = userRepository.findByEmail(prt.getEmail()).orElse(null);
        if (user == null) return "INVALID";

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        prt.setUsed(true);
        tokenRepository.save(prt);

        log.info("Password reset successfully for: {}", prt.getEmail());
        return "OK";
    }

    // ── Google Domain Check via DNS MX Lookup ─────────────────────────────────

    private boolean isGoogleManagedDomain(String email) {
        if (email.endsWith("@gmail.com")) return true;

        String domain = email.substring(email.indexOf('@') + 1);
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            DirContext ctx = new InitialDirContext(env);
            Attributes attrs = ctx.getAttributes("dns:/" + domain, new String[]{"MX"});
            Attribute mx = attrs.get("MX");
            if (mx == null) return false;

            NamingEnumeration<?> records = mx.getAll();
            while (records.hasMore()) {
                String record = records.next().toString().toLowerCase();
                if (record.contains("google.com") || record.contains("googlemail.com")) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.warn("MX DNS lookup failed for domain '{}': {}", domain, e.getMessage());
        }
        return false;
    }
}