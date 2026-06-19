package com.freshersdrive.service;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.entity.DriveRegistration;
import com.freshersdrive.entity.User;
import com.freshersdrive.enums.DriveStatus;
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
    //  NEW — create drive + notify all students
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Save a new drive AND send "New Drive Alert" email to every student.
     * Called from DriveController.createDrive().
     */
    @Transactional
    public Drive createDriveAndNotify(Drive drive) {
        Drive saved = driveRepository.save(drive);

        // Send alert to all (non-admin) users
        List<User> students = userRepository.findAllByRole(Role.ROLE_USER);
        for (User student : students) {
            emailService.sendNewDriveAlert(student, saved);
        }
        log.info("New drive '{}' created — alert sent to {} students.",
                saved.getCompanyName(), students.size());

        return saved;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NEW — cancel drive + notify registered students
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Update drive status to CANCELLED and email every student who registered.
     * Called from DriveController.updateDrive() when status changes to CANCELLED.
     */
    @Transactional
    public Drive cancelDriveAndNotify(Drive drive) {
        drive.setStatus(DriveStatus.CANCELLED); // FIXED: was drive.setStatus("CANCELLED")
        Drive saved = driveRepository.save(drive);

        List<DriveRegistration> registrations = registrationRepository.findByDriveId(saved.getId());
        for (DriveRegistration reg : registrations) {
            emailService.sendDriveCancellationNotice(
                    reg.getStudentEmail(), reg.getStudentName(), saved);
        }
        log.info("Drive '{}' cancelled — notified {} registered students.",
                saved.getCompanyName(), registrations.size());

        return saved;
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NEW — student registers for a drive from the calendar
    // ════════════════════════════════════════════════════════════════════════

    @Transactional
    public void registerStudent(Long driveId, String studentEmail, String studentName) {
        Drive drive = driveRepository.findById(driveId)
                .orElseThrow(() -> new RuntimeException("Drive not found: " + driveId));

        if (registrationRepository.existsByDriveIdAndStudentEmail(driveId, studentEmail)) {
            throw new IllegalStateException("You are already registered for this drive.");
        }

        DriveRegistration reg = new DriveRegistration();
        reg.setDrive(drive);
        reg.setStudentEmail(studentEmail);
        reg.setStudentName(studentName);
        registrationRepository.save(reg);

        // Send confirmation email immediately
        emailService.sendRegistrationConfirmation(reg);

        log.info("Student '{}' registered for drive '{}'.", studentEmail, drive.getCompanyName());
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NEW — Calendar queries
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Returns drives for a specific calendar month — used by the frontend calendar grid.
     * GET /api/drives/calendar?year=2025&month=7
     */
    public List<Drive> getDrivesByMonth(int year, int month) {
        return driveRepository.findByYearAndMonth(year, month);
    }

    /**
     * Returns upcoming drives from today onwards — used by the sidebar list.
     * GET /api/drives/upcoming
     */
    public List<Drive> getUpcomingDrives() {
        return driveRepository.findByDeadlineGreaterThanEqualOrderByDeadlineAsc(LocalDate.now());
    }

    /**
     * Full-text search across company name and job role.
     * GET /api/drives/search?q=TCS
     */
    public List<Drive> searchDrives(String query) {
        return driveRepository.searchDrives(query);
    }

    // ════════════════════════════════════════════════════════════════════════
    //  NEW — Scheduled cron: 9 AM daily reminder to registered students
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Runs every day at 9:00 AM.
     * Finds drives whose deadline is tomorrow and emails all registered students.
     */
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

            // Also uses existing sendDeadlineReminderEmail for subscribed (non-registered) users
            List<User> subscribers = userRepository.findAllByRole(Role.ROLE_USER);
            for (User u : subscribers) {
                emailService.sendDeadlineReminderEmail(u, drive);
            }

            log.info("Reminder sent for '{}' — {} registered + {} subscribed students.",
                    drive.getCompanyName(), registrations.size(), subscribers.size());
        }
    }
}