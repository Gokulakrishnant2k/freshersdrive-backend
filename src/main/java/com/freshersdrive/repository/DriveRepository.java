package com.freshersdrive.repository;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.JobCategory;
import com.freshersdrive.enums.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DriveRepository extends JpaRepository<Drive, Long> {

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

    @Query("""
        SELECT d FROM Drive d
        WHERE d.status != com.freshersdrive.enums.DriveStatus.PENDING
        AND (
            LOWER(d.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.jobRole) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.location) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
    """)
    List<Drive> searchDrives(@Param("keyword") String keyword);

    Page<Drive> findByStatusAndCategory(DriveStatus status, JobCategory category, Pageable pageable);

    Page<Drive> findByStatusOrderByDeadlineAsc(DriveStatus status, Pageable pageable);

    List<Drive> findTop6ByStatusAndIsFeaturedTrueOrderByDeadlineAsc(DriveStatus status);

    List<Drive> findByStatusAndDeadlineGreaterThanEqualOrderByDeadlineAsc(
            DriveStatus status,
            LocalDate date
    );

    List<Drive> findByDeadlineGreaterThanEqualOrderByDeadlineAsc(LocalDate date);

    @Query("SELECT d FROM Drive d WHERE d.deadline BETWEEN :start AND :end")
    List<Drive> findDrivesInDateRange(@Param("start") LocalDate start,
                                       @Param("end") LocalDate end);

    @Query("SELECT d FROM Drive d WHERE YEAR(d.deadline) = :year AND MONTH(d.deadline) = :month")
    List<Drive> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

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

    @Modifying
    @Query("UPDATE Drive d SET d.viewCount = d.viewCount + 1 WHERE d.id = :id")
    void incrementViewCount(@Param("id") Long id);

    @Modifying
    @Query("""
        UPDATE Drive d
        SET d.status = com.freshersdrive.enums.DriveStatus.EXPIRED
        WHERE d.deadline < :today
        AND d.status = com.freshersdrive.enums.DriveStatus.ACTIVE
    """)
    int expireOldDrives(@Param("today") LocalDate today);

    List<Drive> findByDeadlineBefore(LocalDate cutoff);

    long countByStatus(DriveStatus status);

    // =========================
    // AI DISCOVERY / DEDUP SUPPORT
    // =========================
    boolean existsByFingerprint(String fingerprint);

    List<Drive> findByReviewStatus(ReviewStatus reviewStatus);

    long countByReviewStatus(ReviewStatus reviewStatus);

    // =========================
    // REVIEW PANEL SUPPORT
    // =========================

    // Last 5 approved drives for reference panel
    List<Drive> findTop5ByReviewStatusOrderByUpdatedAtDesc(ReviewStatus reviewStatus);

    // Rejected drives older than X days for auto-delete
    List<Drive> findByReviewStatusAndUpdatedAtBefore(ReviewStatus reviewStatus, LocalDateTime cutoff);

    // Approved drives paginated
    Page<Drive> findByReviewStatusOrderByUpdatedAtDesc(ReviewStatus reviewStatus, Pageable pageable);
}