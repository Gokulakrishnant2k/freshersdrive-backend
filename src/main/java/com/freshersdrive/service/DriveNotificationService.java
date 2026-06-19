package com.freshersdrive.service;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.entity.DriveRegistration;
import com.freshersdrive.entity.User;
import com.freshersdrive.enums.Role;
import com.freshersdrive.repository.DriveRegistrationRepository;
import com.freshersdrive.repository.DriveRepository;
import com.freshersdrive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveNotificationService {

    private final DriveRepository             driveRepository;
    private final DriveRegistrationRepository registrationRepository;
    private final UserRepository              userRepository;   // ← added
    private final JavaMailSender              mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy");

    // ════════════════════════════════════════════════════════════════════════
    //  Notification Preference — read & write
    //  Called by DriveController via @AuthenticationPrincipal (JWT email).
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns true if the user has drive email alerts enabled.
     * Defaults to true if the user is not found.
     */
    public boolean getNotifyPreference(String email) {
        return userRepository.findByEmail(email)
                .map(User::isEmailAlerts)
                .orElse(true);
    }

    /**
     * Persists the user's notification toggle preference.
     * The email is extracted from the JWT — no user input required.
     */
    @Transactional
    public void setNotifyPreference(String email, boolean enabled) {
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setEmailAlerts(enabled);
            userRepository.save(user);
            log.info("Notification preference updated for '{}' — enabled: {}", email, enabled);
        });
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Student Registration
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public void registerStudent(Long driveId, String email, String name) {
        Drive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new RuntimeException("Drive not found: " + driveId));

        if (registrationRepository.existsByDriveIdAndStudentEmail(driveId, email)) {
            throw new IllegalStateException("Already registered for this drive.");
        }

        DriveRegistration reg = new DriveRegistration();
        reg.setDrive(drive);
        reg.setStudentEmail(email);
        reg.setStudentName(name);
        registrationRepository.save(reg);

        sendConfirmationEmail(email, name, drive);
        log.info("Student {} registered for drive: {}", email, drive.getCompanyName());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Notify All Students — New Drive Posted
    //  Only sends to students whose emailAlerts toggle is ON.
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void notifyAllStudentsNewDrive(Drive drive) {
        List<User> students = userRepository.findAllByRoleAndEmailAlertsTrue(Role.ROLE_USER);
        for (User student : students) {
            sendNewDriveAlertEmail(student.getEmail(), student.getName(), drive);
        }
        log.info("New drive '{}' — alert sent to {} students with notifications ON.",
                drive.getCompanyName(), students.size());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Notify Registered Students — Drive Cancelled
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void notifyRegisteredStudentsCancellation(Drive drive) {
        List<DriveRegistration> registrations = registrationRepository.findByDriveId(drive.getId());
        for (DriveRegistration reg : registrations) {
            sendCancellationEmail(reg.getStudentEmail(), reg.getStudentName(), drive);
        }
        log.info("Cancellation emails sent to {} students for drive: {}",
                registrations.size(), drive.getCompanyName());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Scheduled: Day-Before Reminder — runs every day at 9:00 AM
    //  Registered students always get reminded regardless of toggle.
    //  Subscribed (non-registered) users only get reminded if toggle is ON.
    // ════════════════════════════════════════════════════════════════════════

    @Scheduled(cron = "0 0 9 * * *")
    public void sendDayBeforeReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Drive> drives = driveRepository.findDrivesInDateRange(tomorrow, tomorrow);

        for (Drive drive : drives) {
            // Always remind students who explicitly registered for this drive
            List<DriveRegistration> registrations =
                    registrationRepository.findByDriveId(drive.getId());
            for (DriveRegistration reg : registrations) {
                sendReminderEmail(reg.getStudentEmail(), reg.getStudentName(), drive);
            }

            // Only remind general subscribers who have emailAlerts ON
            List<User> subscribers =
                    userRepository.findAllByRoleAndEmailAlertsTrue(Role.ROLE_USER);
            for (User u : subscribers) {
                sendReminderEmail(u.getEmail(), u.getName(), drive);
            }

            log.info("Reminder sent for '{}' — {} registered + {} subscribed students.",
                    drive.getCompanyName(), registrations.size(), subscribers.size());
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Email Senders
    // ════════════════════════════════════════════════════════════════════════

    @Async
    public void sendConfirmationEmail(String to, String name, Drive drive) {
        String subject = "✅ Registered: " + drive.getCompanyName() + " – " + drive.getJobRole();
        String body = emailWrapper("Registration Confirmed ✅", "#059669",
            "<p>Hi <strong>" + name + "</strong>,</p>" +
            "<p>You've successfully registered for this drive. Best of luck! 🎯</p>" +
            driveTable(drive) +
            tipBox("📌 Remember to:",
                "<li>Carry 3–4 printed copies of your resume</li>" +
                "<li>Bring your college ID and Aadhar card</li>" +
                "<li>Arrive 30 minutes early</li>")
        );
        send(to, subject, body);
    }

    @Async
    public void sendReminderEmail(String to, String name, Drive drive) {
        String subject = "⏰ Reminder: " + drive.getCompanyName() + " Drive is Tomorrow!";
        String body = emailWrapper("Drive Reminder ⏰", "#d97706",
            "<p>Hi <strong>" + name + "</strong>,</p>" +
            "<p>Just a heads-up — your placement drive is <strong>tomorrow</strong>. Be prepared!</p>" +
            driveTable(drive) +
            tipBox("🗓️ Tomorrow's checklist:",
                "<li>Resume copies ready?</li>" +
                "<li>Formal dress code?</li>" +
                "<li>Venue confirmed?</li>")
        );
        send(to, subject, body);
    }

    @Async
    public void sendCancellationEmail(String to, String name, Drive drive) {
        String subject = "❌ Drive Cancelled: " + drive.getCompanyName();
        String body = emailWrapper("Drive Cancelled ❌", "#dc2626",
            "<p>Hi <strong>" + name + "</strong>,</p>" +
            "<p>We're sorry to inform you that the following drive has been <strong>cancelled</strong>.</p>" +
            driveTable(drive) +
            "<p style='color:#6b7280;margin-top:16px;'>We apologize for the inconvenience. " +
            "Check FreshersDrive regularly for new opportunities!</p>"
        );
        send(to, subject, body);
    }

    @Async
    public void sendNewDriveAlertEmail(String to, String name, Drive drive) {
        String subject = "🚀 New Drive: " + drive.getCompanyName() + " | " + drive.getJobRole();
        String body = emailWrapper("New Drive Alert! 🚀", "#4f46e5",
            "<p>Hi <strong>" + name + "</strong>,</p>" +
            "<p>A new placement drive has been posted. Check it out and apply before the deadline!</p>" +
            driveTable(drive) +
            "<div style='text-align:center;margin-top:24px;'>" +
            "<a href='http://localhost:3000/drives/" + drive.getId() + "' " +
            "style='background:#4f46e5;color:#fff;padding:12px 28px;border-radius:8px;" +
            "text-decoration:none;font-weight:600;font-size:15px;'>View & Apply Now</a></div>" +
            "<p style='color:#9ca3af;font-size:12px;margin-top:20px;'>You're receiving this because " +
            "you have drive alerts enabled on FreshersDrive. You can turn this off from the Drive Calendar page.</p>"
        );
        send(to, subject, body);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  Internal helpers
    // ════════════════════════════════════════════════════════════════════════

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(msg);
            log.info("Email sent → {} | {}", to, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending email to {}: {}", to, e.getMessage());
        }
    }

    private String driveTable(Drive d) {
        String deadline = d.getDeadline() != null ? d.getDeadline().format(DATE_FMT) : "TBD";
        String ctc = (d.getCtcMin() != null && d.getCtcMax() != null)
            ? d.getCtcMin() + " – " + d.getCtcMax() + " LPA"
            : (d.getCtcDisplay() != null ? d.getCtcDisplay() : "Not disclosed");

        return "<table style='width:100%;border-collapse:collapse;margin-top:16px;'>" +
               row("🏢 Company",   d.getCompanyName())  +
               row("💼 Role",      d.getJobRole())       +
               row("📍 Location",  d.getLocation() != null ? d.getLocation() : "TBD") +
               row("📅 Deadline",  deadline)             +
               row("💰 CTC",       ctc)                  +
               row("🎓 Min CGPA",  d.getMinCgpa() != null ? String.valueOf(d.getMinCgpa()) : "Any") +
               row("🔖 Branches",  d.getEligibleBranches() != null ? d.getEligibleBranches() : "All Branches") +
               "</table>";
    }

    private String row(String label, String value) {
        return "<tr>" +
               "<td style='padding:8px 12px;background:#f9fafb;border:1px solid #e5e7eb;" +
               "width:38%;font-weight:600;color:#374151;'>" + label + "</td>" +
               "<td style='padding:8px 12px;border:1px solid #e5e7eb;color:#111827;'>" + value + "</td>" +
               "</tr>";
    }

    private String tipBox(String title, String listItems) {
        return "<div style='background:#fffbeb;border-left:4px solid #d97706;padding:12px 16px;" +
               "margin-top:16px;border-radius:4px;'>" +
               "<strong>" + title + "</strong><ul style='margin:8px 0 0;padding-left:20px;'>" +
               listItems + "</ul></div>";
    }

    private String emailWrapper(String title, String color, String content) {
        return "<!DOCTYPE html><html><body style='margin:0;padding:0;background:#f3f4f6;" +
               "font-family:Arial,sans-serif;'>" +
               "<div style='max-width:600px;margin:32px auto;background:#fff;border-radius:12px;" +
               "overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,.1);'>" +
               "<div style='background:" + color + ";padding:24px 32px;'>" +
               "<h1 style='color:#fff;margin:0;font-size:22px;'>FreshersDrive</h1>" +
               "<p style='color:rgba(255,255,255,.85);margin:4px 0 0;font-size:13px;'>" +
               "Placement Drive Tracker for Freshers</p></div>" +
               "<div style='padding:28px 32px;'>" +
               "<h2 style='color:" + color + ";margin-top:0;'>" + title + "</h2>" +
               content + "</div>" +
               "<div style='background:#f9fafb;padding:14px 32px;text-align:center;" +
               "border-top:1px solid #e5e7eb;'>" +
               "<p style='color:#9ca3af;font-size:12px;margin:0;'>" +
               "© 2025 FreshersDrive. You received this because you're registered on our platform.</p>" +
               "</div></div></body></html>";
    }
}