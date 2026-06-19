package com.freshersdrive.service;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.entity.DriveRegistration;
import com.freshersdrive.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.format.DateTimeFormatter;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${app.base-url:http://localhost:5173}")
    private String baseUrl;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ════════════════════════════════════════════════════════════════════════
    //  EXISTING — Auth emails (unchanged)
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void sendVerificationEmail(User user, String token) {
        String subject = "Verify your FreshersDrive account";
        String verifyUrl = "http://localhost:3000/verify-email?token=" + token;
        sendHtmlEmail(user.getEmail(), subject, buildVerificationEmailBody(user.getName(), verifyUrl));
    }

@Async
public void sendPasswordResetEmail(User user, String token) {
    String subject = "Reset your FreshersDrive password";

    String resetUrl = baseUrl + "/reset-password?token=" + token;

    sendHtmlEmail(
        user.getEmail(),
        subject,
        buildPasswordResetEmailBody(user.getName(), resetUrl)
    );
}

    // ════════════════════════════════════════════════════════════════════════
    //  EXISTING — Drive alert & deadline reminder (unchanged)
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void sendNewDriveAlert(User user, Drive drive) {
        String subject = "🚀 New Drive: " + drive.getCompanyName() + " | " + drive.getJobRole();
        sendHtmlEmail(user.getEmail(), subject, buildDriveAlertEmailBody(user.getName(), drive));
    }

    @Async
    public void sendDeadlineReminderEmail(User user, Drive drive) {
        String subject = "⏰ Deadline tomorrow: " + drive.getCompanyName();
        sendHtmlEmail(user.getEmail(), subject, buildDeadlineReminderBody(user.getName(), drive));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NEW — Calendar registration emails
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sent immediately when a student registers for a drive from the calendar.
     */
    @Async
    public void sendRegistrationConfirmation(DriveRegistration registration) {
        Drive drive = registration.getDrive();
        String subject = "✅ Registered: " + drive.getCompanyName() + " – " + drive.getJobRole();
        sendHtmlEmail(
            registration.getStudentEmail(),
            subject,
            buildRegistrationConfirmationBody(registration.getStudentName(), drive)
        );
    }

    /**
     * Sent the day before the drive deadline to all registered students.
     * Called from DriveService scheduled cron job.
     */
    @Async
    public void sendDriveReminderToRegistered(String toEmail, String studentName, Drive drive) {
        String subject = "⏰ Reminder: " + drive.getCompanyName() + " Drive is Tomorrow!";
        sendHtmlEmail(toEmail, subject, buildRegisteredReminderBody(studentName, drive));
    }

    /**
     * Sent to all registered students when a drive is cancelled by admin.
     */
    @Async
    public void sendDriveCancellationNotice(String toEmail, String studentName, Drive drive) {
        String subject = "❌ Drive Cancelled: " + drive.getCompanyName();
        sendHtmlEmail(toEmail, subject, buildCancellationBody(studentName, drive));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NEW — Contact form
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Sent to the site's own inbox (app.mail.from) whenever a visitor
     * submits the public Contact page form.
     */
    @Async
    public void sendContactFormEmail(String name, String fromEmail, String userMessage) {
        String subject = "📩 New Contact Form Message from " + name;
        sendHtmlEmail(from, subject, buildContactFormBody(name, fromEmail, userMessage));
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PRIVATE — Core send method
    // ════════════════════════════════════════════════════════════════════════

    private void sendHtmlEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to: {}", to);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  PRIVATE — Email body builders
    // ════════════════════════════════════════════════════════════════════════

    // ── Existing: Verification ───────────────────────────────────────────────
    private String buildVerificationEmailBody(String name, String verifyUrl) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9f9f9">
              <div style="background:#1a1a2e;padding:20px;border-radius:8px 8px 0 0;text-align:center">
                <h1 style="color:#e94560;margin:0">FreshersDrive</h1>
                <p style="color:#a0a0b0;margin:5px 0">Your Career Launchpad</p>
              </div>
              <div style="background:#ffffff;padding:30px;border-radius:0 0 8px 8px">
                <h2 style="color:#1a1a2e">Hello, %s! 👋</h2>
                <p style="color:#555;line-height:1.6">
                  Welcome to FreshersDrive! We're excited to have you.
                  Click the button below to verify your email address.
                </p>
                <div style="text-align:center;margin:30px 0">
                  <a href="%s" style="background:#e94560;color:white;padding:14px 32px;border-radius:6px;text-decoration:none;font-weight:bold;font-size:16px">
                    Verify My Account
                  </a>
                </div>
                <p style="color:#888;font-size:13px">This link expires in 24 hours. If you didn't create an account, ignore this email.</p>
              </div>
            </div>
            """.formatted(name, verifyUrl);
    }

    // ── Existing: Password Reset ─────────────────────────────────────────────
    private String buildPasswordResetEmailBody(String name, String resetUrl) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px">
              <h2>Hi %s, reset your password</h2>
              <a href="%s" style="background:#e94560;color:white;padding:12px 24px;border-radius:6px;text-decoration:none">Reset Password</a>
              <p style="color:#888;font-size:12px">Expires in 1 hour. If you didn't request this, ignore the email.</p>
            </div>
            """.formatted(name, resetUrl);
    }

    // ── Existing: New Drive Alert ────────────────────────────────────────────
    private String buildDriveAlertEmailBody(String name, Drive drive) {
        String deadline = drive.getDeadline().format(DATE_FMT);
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9f9f9">
              <div style="background:#1a1a2e;padding:20px;border-radius:8px 8px 0 0;text-align:center">
                <h1 style="color:#e94560;margin:0">FreshersDrive</h1>
              </div>
              <div style="background:#ffffff;padding:30px;border-radius:0 0 8px 8px">
                <p style="color:#555">Hi %s,</p>
                <h2 style="color:#1a1a2e">New Drive Alert! 🚀</h2>
                <div style="background:#f4f4f8;border-left:4px solid #e94560;padding:20px;border-radius:4px;margin:20px 0">
                  <h3 style="margin:0 0 10px;color:#1a1a2e">%s</h3>
                  <p style="margin:5px 0;color:#555"><strong>Role:</strong> %s</p>
                  <p style="margin:5px 0;color:#555"><strong>Package:</strong> %s</p>
                  <p style="margin:5px 0;color:#555"><strong>Location:</strong> %s</p>
                  <p style="margin:5px 0;color:#e94560"><strong>⏰ Apply before:</strong> %s</p>
                </div>
                <div style="text-align:center;margin:25px 0">
                  <a href="http://localhost:3000/drives/%s" style="background:#e94560;color:white;padding:14px 32px;border-radius:6px;text-decoration:none;font-weight:bold">
                    View &amp; Apply Now
                  </a>
                </div>
                <p style="color:#888;font-size:12px">You're receiving this because you subscribed to job alerts. <a href="#">Unsubscribe</a></p>
              </div>
            </div>
            """.formatted(name, drive.getCompanyName(), drive.getJobRole(),
                drive.getCtcDisplay() != null ? drive.getCtcDisplay() : "As per norms",
                drive.getLocation() != null ? drive.getLocation() : "Multiple Locations",
                deadline, drive.getId());
    }

    // ── Existing: Deadline Reminder (for subscribed users) ──────────────────
    private String buildDeadlineReminderBody(String name, Drive drive) {
        return """
            <div style="font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px">
              <h2>⏰ Hi %s, deadline tomorrow!</h2>
              <p><strong>%s - %s</strong> closes tomorrow. Don't miss out!</p>
              <a href="http://localhost:3000/drives/%s" style="background:#e94560;color:white;padding:12px 24px;border-radius:6px;text-decoration:none">Apply Now</a>
            </div>
            """.formatted(name, drive.getCompanyName(), drive.getJobRole(), drive.getId());
    }

    // ── NEW: Registration Confirmation ───────────────────────────────────────
    private String buildRegistrationConfirmationBody(String name, Drive drive) {
        String deadline = drive.getDeadline() != null ? drive.getDeadline().format(DATE_FMT) : "TBD";
        String ctc = drive.getCtcDisplay() != null ? drive.getCtcDisplay()
                   : (drive.getCtcMin() != null ? drive.getCtcMin() + " – " + drive.getCtcMax() + " LPA" : "As per norms");
        return wrapper("✅ Registration Confirmed!", "#059669",
            "<p style='color:#555'>Hi <strong>" + name + "</strong>,</p>" +
            "<p style='color:#555'>You've successfully registered for the drive below. Best of luck! 🎯</p>" +
            driveInfoBlock(drive, deadline, ctc) +
            tipBox("📌 Remember to bring:",
                "<li>3–4 printed copies of your resume</li>" +
                "<li>College ID card and Aadhar card</li>" +
                "<li>Arrive 30 minutes early to the venue</li>" +
                "<li>Formal dress code is mandatory</li>") +
            "<p style='color:#888;font-size:12px;margin-top:20px'>You registered for this drive via FreshersDrive Calendar.</p>"
        );
    }

    // ── NEW: Reminder for registered students (day before) ──────────────────
    private String buildRegisteredReminderBody(String name, Drive drive) {
        String deadline = drive.getDeadline() != null ? drive.getDeadline().format(DATE_FMT) : "TBD";
        String ctc = drive.getCtcDisplay() != null ? drive.getCtcDisplay() : "As per norms";
        return wrapper("⏰ Drive is Tomorrow!", "#d97706",
            "<p style='color:#555'>Hi <strong>" + name + "</strong>,</p>" +
            "<p style='color:#555'>This is a reminder — your placement drive is <strong>tomorrow</strong>. Get ready!</p>" +
            driveInfoBlock(drive, deadline, ctc) +
            tipBox("🗓️ Tomorrow's checklist:",
                "<li>Resume copies ready?</li>" +
                "<li>Formal dress set out?</li>" +
                "<li>Venue address confirmed?</li>" +
                "<li>Phone fully charged?</li>") +
            "<p style='color:#888;font-size:12px;margin-top:20px'>You're receiving this because you registered for this drive on FreshersDrive.</p>"
        );
    }

    // ── NEW: Cancellation Notice ─────────────────────────────────────────────
    private String buildCancellationBody(String name, Drive drive) {
        String deadline = drive.getDeadline() != null ? drive.getDeadline().format(DATE_FMT) : "TBD";
        String ctc = drive.getCtcDisplay() != null ? drive.getCtcDisplay() : "As per norms";
        return wrapper("❌ Drive Cancelled", "#dc2626",
            "<p style='color:#555'>Hi <strong>" + name + "</strong>,</p>" +
            "<p style='color:#555'>We regret to inform you that the following drive has been <strong>cancelled</strong>.</p>" +
            driveInfoBlock(drive, deadline, ctc) +
            "<div style='background:#fef2f2;border-left:4px solid #dc2626;padding:12px 16px;border-radius:4px;margin-top:16px;'>" +
            "We apologize for the inconvenience. Keep checking FreshersDrive for new opportunities!" +
            "</div>" +
            "<p style='color:#888;font-size:12px;margin-top:20px'>You registered for this drive via FreshersDrive Calendar.</p>"
        );
    }

    // ── NEW: Contact Form ─────────────────────────────────────────────────────
    private String buildContactFormBody(String name, String fromEmail, String userMessage) {
        return wrapper("📩 New Contact Form Submission", "#2563eb",
            "<p style='color:#555'><strong>From:</strong> " + name + " (" + fromEmail + ")</p>" +
            "<div style='background:#f4f4f8;border-left:4px solid #2563eb;padding:16px;border-radius:4px;margin:16px 0;white-space:pre-wrap;color:#374151'>" +
            userMessage +
            "</div>" +
            "<p style='color:#888;font-size:12px;margin-top:20px'>Sent via the FreshersDrive contact form.</p>"
        );
    }

    // ── Shared HTML helpers ──────────────────────────────────────────────────

    private String driveInfoBlock(Drive drive, String deadline, String ctc) {
        return "<div style='background:#f4f4f8;border-left:4px solid #e94560;padding:20px;border-radius:4px;margin:20px 0'>" +
               "<h3 style='margin:0 0 10px;color:#1a1a2e'>" + drive.getCompanyName() + "</h3>" +
               "<p style='margin:5px 0;color:#555'><strong>Role:</strong> " + drive.getJobRole() + "</p>" +
               "<p style='margin:5px 0;color:#555'><strong>Location:</strong> " + (drive.getLocation() != null ? drive.getLocation() : "TBD") + "</p>" +
               "<p style='margin:5px 0;color:#555'><strong>Package:</strong> " + ctc + "</p>" +
               "<p style='margin:5px 0;color:#555'><strong>Min CGPA:</strong> " + (drive.getMinCgpa() != null ? drive.getMinCgpa() : "Any") + "</p>" +
               "<p style='margin:5px 0;color:#555'><strong>Eligible Branches:</strong> " + (drive.getEligibleBranches() != null ? drive.getEligibleBranches() : "All Branches") + "</p>" +
               "<p style='margin:5px 0;color:#e94560'><strong>⏰ Deadline:</strong> " + deadline + "</p>" +
               "</div>";
    }

    private String tipBox(String title, String listItems) {
        return "<div style='background:#f0fdf4;border-left:4px solid #059669;padding:12px 16px;border-radius:4px;margin-top:16px'>" +
               "<strong>" + title + "</strong>" +
               "<ul style='margin:8px 0 0;padding-left:20px;color:#374151'>" + listItems + "</ul>" +
               "</div>";
    }

    private String wrapper(String title, String accentColor, String content) {
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;padding:20px;background:#f9f9f9'>" +
               "<div style='background:#1a1a2e;padding:20px;border-radius:8px 8px 0 0;text-align:center'>" +
               "<h1 style='color:#e94560;margin:0'>FreshersDrive</h1>" +
               "<p style='color:#a0a0b0;margin:5px 0'>Your Career Launchpad</p>" +
               "</div>" +
               "<div style='background:#ffffff;padding:30px;border-radius:0 0 8px 8px'>" +
               "<h2 style='color:" + accentColor + ";margin-top:0'>" + title + "</h2>" +
               content +
               "</div>" +
               "<div style='padding:12px;text-align:center;background:#f4f4f4;border-top:1px solid #e0e0e0'>" +
               "<p style='color:#aaa;font-size:11px;margin:0'>© 2025 FreshersDrive. Placement Drive Tracker for Freshers.</p>" +
               "</div></div>";
    }
}