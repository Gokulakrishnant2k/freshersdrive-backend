package com.freshersdrive.service;

import com.freshersdrive.entity.Drive;
import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.ReviewStatus;
import com.freshersdrive.repository.DriveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DriveReviewService {

    private final DriveRepository driveRepository;

    public List<Drive> getPendingDrives() {
        return driveRepository.findByReviewStatus(ReviewStatus.PENDING_REVIEW);
    }

    public Drive approve(Long driveId) {
        Drive drive = getOrThrow(driveId);
        drive.setReviewStatus(ReviewStatus.APPROVED);
        drive.setStatus(DriveStatus.UPCOMING);
        return driveRepository.save(drive);
    }

    public Drive reject(Long driveId) {
        Drive drive = getOrThrow(driveId);
        drive.setReviewStatus(ReviewStatus.REJECTED);
        return driveRepository.save(drive);
    }

    private Drive getOrThrow(Long driveId) {
        return driveRepository.findById(driveId)
            .orElseThrow(() -> new NoSuchElementException("Drive not found: " + driveId));
    }
}