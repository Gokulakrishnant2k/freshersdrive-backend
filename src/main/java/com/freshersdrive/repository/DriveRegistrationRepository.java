package com.freshersdrive.repository;

import com.freshersdrive.entity.DriveRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriveRegistrationRepository extends JpaRepository<DriveRegistration, Long> {

    List<DriveRegistration> findByDriveId(Long driveId);

    Optional<DriveRegistration> findByDriveIdAndStudentEmail(Long driveId, String email);

    boolean existsByDriveIdAndStudentEmail(Long driveId, String email);

    int countByDriveId(Long driveId);
}