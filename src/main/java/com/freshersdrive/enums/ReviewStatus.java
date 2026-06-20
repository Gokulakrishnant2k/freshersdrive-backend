package com.freshersdrive.enums;

/**
 * Tracks the review state of a Drive that came in through AI-assisted
 * discovery, separate from DriveStatus (which describes the drive's
 * live lifecycle once published: ACTIVE, CLOSED, UPCOMING, etc.).
 *
 * Manually-added drives (via AddDrive) are always APPROVED immediately.
 *
 * NOTE: Drive.status (DriveStatus) is non-nullable, so every row also gets
 * DriveStatus.PENDING while ReviewStatus is PENDING_REVIEW — see
 * DriveStatus.PENDING for the lifecycle-side half of this.
 */
public enum ReviewStatus {
    PENDING_REVIEW,
    APPROVED,
    REJECTED
}