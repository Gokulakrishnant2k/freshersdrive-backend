package com.freshersdrive.service;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.entity.DriveRegistration;
import com.freshersdrive.entity.User;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.ReviewStatus;
import com.freshersdrive.enums.Role;
import com.freshersdrive.repository.DriveRegistrationRepository;
import com.freshersdrive.repository.DriveRepository;
import com.freshersdrive.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveService {

    private final DriveRepository             driveRepository;
    private final DriveRegistrationRepository registrationRepository;
    private final UserRepository              userRepository;
    private final EmailService                emailService;

    // ════════════════════════════════════════════════════════════════════════
    //  EXISTING — basic CRUD (unchanged)
    // ════════════════════════════════════════════════════════════════════════

    public List<Drive> getAllDrives() {
        return driveRepository.findAll();
    }

    public Drive getDriveById(Long id) {
        return driveRepository.findById(id).orElse(null);
    }

    public Drive save(Drive drive) {
        return driveRepository.save(drive);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CREATE — save drive WITHOUT notifying students yet.
    //  Notification is deferred until DriveReviewService.approve() is called.
    //  The drive starts at ReviewStatus.PENDING_REVIEW (set by Drive entity
    //  default), so it is invisible to students until an admin/employee
    //  approves it.
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public Drive createDrive(Drive drive) {
        Drive saved = driveRepository.save(drive);
        log.info("Drive '{}' saved with status PENDING_REVIEW — awaiting approval before going live.",
                saved.getCompanyName());
        return saved;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NOTIFY — called by DriveReviewService.approve() AFTER approval.
    //  Sends "New Drive Alert" to every student only once the drive is live.
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public void notifyStudentsOfNewDrive(Drive drive) {
        List<User> students = userRepository.findAllByRole(Role.ROLE_USER);
        for (User student : students) {
            emailService.sendNewDriveAlert(student, drive);
        }
        log.info("New drive alert for '{}' sent to {} student(s).",
                drive.getCompanyName(), students.size());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CANCEL — update status to CANCELLED and notify registered students.
    //  Called from DriveController when status changes to CANCELLED.
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public Drive cancelDriveAndNotify(Drive drive) {
        drive.setStatus(DriveStatus.CANCELLED);
        Drive saved = driveRepository.save(drive);

        List<DriveRegistration> registrations = registrationRepository.findByDriveId(saved.getId());
        for (DriveRegistration reg : registrations) {
            emailService.sendDriveCancellationNotice(
                    reg.getStudentEmail(), reg.getStudentName(), saved);
        }
        log.info("Drive '{}' cancelled — notified {} registered student(s).",
                saved.getCompanyName(), registrations.size());

        return saved;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  STUDENT REGISTRATION
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public void registerStudent(Long driveId, String studentEmail, String studentName) {
        Drive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new RuntimeException("Drive not found: " + driveId));

        // Guard: students must not be able to register for unapproved drives
        if (drive.getReviewStatus() != ReviewStatus.APPROVED) {
            throw new IllegalStateException("Drive is not yet available for registration.");
        }

        if (registrationRepository.existsByDriveIdAndStudentEmail(driveId, studentEmail)) {
            throw new IllegalStateException("You are already registered for this drive.");
        }

        DriveRegistration reg = new DriveRegistration();
        reg.setDrive(drive);
        reg.setStudentEmail(studentEmail);
        reg.setStudentName(studentName);
        registrationRepository.save(reg);

        emailService.sendRegistrationConfirmation(reg);
        log.info("Student '{}' registered for drive '{}'.", studentEmail, drive.getCompanyName());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  CALENDAR QUERIES — all filtered to APPROVED drives only (via repo)
    // ════════════════════════════════════════════════════════════════════════

    public List<Drive> getDrivesByMonth(int year, int month) {
        return driveRepository.findByYearAndMonth(year, month);
    }

    public List<Drive> getUpcomingDrives() {
        return driveRepository.findByDeadlineGreaterThanEqualOrderByDeadlineAsc(LocalDate.now());
    }

    public List<Drive> searchDrives(String query) {
        return driveRepository.searchDrives(query);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  SCHEDULED — 9 AM daily deadline reminder to registered students
    // ════════════════════════════════════════════════════════════════════════

    @Scheduled(cron = "0 0 9 * * *")
    public void sendDayBeforeReminders() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        List<Drive> drives = driveRepository.findDrivesInDateRange(tomorrow, tomorrow);

        for (Drive drive : drives) {
            List<DriveRegistration> registrations =
                    registrationRepository.findByDriveId(drive.getId());

            for (DriveRegistration reg : registrations) {
                emailService.sendDriveReminderToRegistered(
                        reg.getStudentEmail(), reg.getStudentName(), drive);
            }

            List<User> subscribers = userRepository.findAllByRole(Role.ROLE_USER);
            for (User u : subscribers) {
                emailService.sendDeadlineReminderEmail(u, drive);
            }

            log.info("Reminder sent for '{}' — {} registered + {} subscribed student(s).",
                    drive.getCompanyName(), registrations.size(), subscribers.size());
        }
    }
    // ════════════════════════════════════════════════════════════════════════
//  LOCATION NORMALIZATION — reuses existing casing ("Pan India") instead
//  of inserting duplicates from manual entry, AI discovery, or RSS import.
// ════════════════════════════════════════════════════════════════════════

public String normalizeLocation(String location) {
    if (location == null || location.isBlank()) return location;
    String cleaned = location.trim().replaceAll("\\s+", " ");

    return driveRepository.findFirstByLocationIgnoreCase(cleaned)
            .map(Drive::getLocation)
            .orElse(cleaned);
}
}