package com.freshersdrive.service;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.repository.DriveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DriveCalendarService {

    private final DriveRepository driveRepository;

    /**
     * All drives in a given calendar month — used by the frontend calendar grid.
     */
    public List<Drive> getDrivesByMonth(int year, int month) {
        return driveRepository.findByYearAndMonth(year, month);
    }

    /**
     * Drives from today onwards — used by the "Upcoming Drives" sidebar.
     */
    public List<Drive> getUpcomingDrives() {
        return driveRepository.findByDeadlineGreaterThanEqualOrderByDeadlineAsc(LocalDate.now());
    }

    /**
     * Full-text search across company name and job role.
     */
    public List<Drive> searchDrives(String query) {
        return driveRepository.searchDrives(query);
    }
}