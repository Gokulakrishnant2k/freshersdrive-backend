package com.freshersdrive.repository;

import com.freshersdrive.entity.UserApplication;
import com.freshersdrive.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserApplicationRepository extends JpaRepository<UserApplication, Long> {

    List<UserApplication> findByUserId(Long userId);

    Optional<UserApplication> findByUserIdAndDriveId(Long userId, Long driveId);

    boolean existsByUserIdAndDriveId(Long userId, Long driveId);

    List<UserApplication> findByUserIdAndStatus(Long userId, ApplicationStatus status);

    @Query("SELECT COUNT(ua) FROM UserApplication ua WHERE ua.user.id = :userId AND ua.status = :status")
    Long countByUserIdAndStatus(@Param("userId") Long userId, @Param("status") ApplicationStatus status);

    // Dashboard stats for a user
    @Query("SELECT ua.status, COUNT(ua) FROM UserApplication ua WHERE ua.user.id = :userId GROUP BY ua.status")
    List<Object[]> countByUserIdGroupByStatus(@Param("userId") Long userId);
}