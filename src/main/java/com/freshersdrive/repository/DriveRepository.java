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

    // ── Public search: only APPROVED drives ───────────────────────────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
        AND (
            LOWER(d.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.jobRole) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.location) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
    """)
    Page<Drive> searchDrives(@Param("keyword") String keyword,
                             @Param("status") DriveStatus status,
                             Pageable pageable);

    // ── Public list search: only APPROVED and not PENDING drives ──────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status != com.freshersdrive.enums.DriveStatus.PENDING
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
        AND (
            LOWER(d.companyName) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.jobRole) LIKE LOWER(CONCAT('%', :keyword, '%'))
            OR LOWER(d.location) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
    """)
    List<Drive> searchDrives(@Param("keyword") String keyword);

    // ── Public paginated by status + category: only APPROVED ──────────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND d.category = :category
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
    """)
    Page<Drive> findByStatusAndCategory(@Param("status") DriveStatus status,
                                        @Param("category") JobCategory category,
                                        Pageable pageable);

    // ── Public paginated by status ordered by deadline: only APPROVED ──────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
        ORDER BY d.deadline ASC
    """)
    Page<Drive> findByStatusOrderByDeadlineAsc(@Param("status") DriveStatus status,
                                               Pageable pageable);

    // ── Featured drives: only APPROVED ────────────────────────────────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND d.isFeatured = true
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
        ORDER BY d.deadline ASC
    """)
    List<Drive> findTop6ByStatusAndIsFeaturedTrueOrderByDeadlineAsc(
            @Param("status") DriveStatus status);

    // ── Upcoming drives from a date: only APPROVED ─────────────────────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND d.deadline >= :date
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
        ORDER BY d.deadline ASC
    """)
    List<Drive> findByStatusAndDeadlineGreaterThanEqualOrderByDeadlineAsc(
            @Param("status") DriveStatus status,
            @Param("date") LocalDate date);

    // ── All upcoming from a date (no status filter): only APPROVED ─────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.deadline >= :date
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
        ORDER BY d.deadline ASC
    """)
    List<Drive> findByDeadlineGreaterThanEqualOrderByDeadlineAsc(@Param("date") LocalDate date);

    // ── Date range (calendar/reminders): only APPROVED ────────────────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.deadline BETWEEN :start AND :end
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
    """)
    List<Drive> findDrivesInDateRange(@Param("start") LocalDate start,
                                      @Param("end") LocalDate end);

    // ── Calendar month view: only APPROVED ────────────────────────────────
    @Query("""
        SELECT d FROM Drive d
        WHERE YEAR(d.deadline) = :year
        AND MONTH(d.deadline) = :month
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
    """)
    List<Drive> findByYearAndMonth(@Param("year") int year, @Param("month") int month);

    // ── Eligible drives for a student profile: only APPROVED ──────────────
    @Query("""
        SELECT d FROM Drive d
        WHERE d.status = :status
        AND d.reviewStatus = com.freshersdrive.enums.ReviewStatus.APPROVED
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

    List<Drive> findTop5ByReviewStatusOrderByUpdatedAtDesc(ReviewStatus reviewStatus);

    List<Drive> findByReviewStatusAndUpdatedAtBefore(ReviewStatus reviewStatus, LocalDateTime cutoff);

    Page<Drive> findByReviewStatusOrderByUpdatedAtDesc(ReviewStatus reviewStatus, Pageable pageable);
}