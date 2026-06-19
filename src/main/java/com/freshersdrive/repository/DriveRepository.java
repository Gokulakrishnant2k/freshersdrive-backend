package com.freshersdrive.repository;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.JobCategory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DriveRepository extends JpaRepository<Drive, Long> {

    // =========================
    // SEARCH DRIVES (paginated, status-filtered)
    // =========================
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND (
            LOWER(d.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.jobRole) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.location) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
    """)
    Page<Drive> searchDrives(@Param("keyword") String keyword,
                             @Param("status") DriveStatus status,
                             Pageable pageable);

    // =========================
    // SEARCH DRIVES (simple, no pagination/status — used by calendar/search bar)
    // =========================
    @Query("""
        SELECT d FROM Drive d
        WHERE LOWER(d.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(d.jobRole) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(d.location) LIKE LOWER(CONCAT('%', :keyword, '%'))
    """)
    List<Drive> searchDrives(@Param("keyword") String keyword);

    // =========================
    // FILTER BY CATEGORY
    // =========================
    Page<Drive> findByStatusAndCategory(DriveStatus status, JobCategory category, Pageable pageable);

    // =========================
    // ACTIVE DRIVES ORDERED
    // =========================
    Page<Drive> findByStatusOrderByDeadlineAsc(DriveStatus status, Pageable pageable);

    // =========================
    // FEATURED DRIVES
    // =========================
    List<Drive> findTop6ByStatusAndIsFeaturedTrueOrderByDeadlineAsc(DriveStatus status);

    // =========================
    // UPCOMING DEADLINES (status-filtered)
    // =========================
    List<Drive> findByStatusAndDeadlineGreaterThanEqualOrderByDeadlineAsc(
            DriveStatus status,
            LocalDate date
    );

    // =========================
    // UPCOMING DEADLINES (any status — used by the sidebar)
    // =========================
    List<Drive> findByDeadlineGreaterThanEqualOrderByDeadlineAsc(LocalDate date);

    // =========================
    // DRIVES WITHIN A DATE RANGE (used by the day-before reminder job)
    // =========================
    @Query("SELECT d FROM Drive d WHERE d.deadline BETWEEN :start AND :end")
    List<Drive> findDrivesInDateRange(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    // =========================
    // DRIVES IN A GIVEN CALENDAR MONTH (used by the calendar grid)
    // =========================
    @Query("SELECT d FROM Drive d WHERE YEAR(d.deadline) = :year AND MONTH(d.deadline) = :month")
    List<Drive> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    // =========================
    // ELIGIBILITY CHECK
    // =========================
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND d.minCgpa <= :cgpa
        AND (d.eligibleBatches LIKE CONCAT('%', :batch, '%'))
        AND (d.eligibleBranches LIKE '%ALL%' OR d.eligibleBranches LIKE CONCAT('%', :branch, '%'))
        AND (d.eligibleDegrees LIKE '%ALL%' OR d.eligibleDegrees LIKE CONCAT('%', :degree, '%'))
    """)
    List<Drive> findEligibleDrives(@Param("status") DriveStatus status,
                                   @Param("cgpa") Double cgpa,
                                   @Param("batch") Integer batch,
                                   @Param("branch") String branch,
                                   @Param("degree") String degree);

    // =========================
    // VIEW COUNT INCREMENT
    // =========================
    @Modifying
    @Query("UPDATE Drive d SET d.viewCount = d.viewCount + 1 WHERE d.id = :id")
    void incrementViewCount(@Param("id") Long id);

    // =========================
    // EXPIRE OLD DRIVES
    // =========================
    @Modifying
    @Query("""
        UPDATE Drive d
        SET d.status = com.freshersdrive.enums.DriveStatus.EXPIRED
        WHERE d.deadline < :today
        AND d.status = com.freshersdrive.enums.DriveStatus.ACTIVE
    """)
    int expireOldDrives(@Param("today") LocalDate today);

    // =========================
    // 🔥 AUTO DELETE SUPPORT
    // =========================
    List<Drive> findByDeadlineBefore(LocalDate cutoff);

    // =========================
    // STATS
    // =========================
    long countByStatus(DriveStatus status);
}