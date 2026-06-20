package com.freshersdrive.enums;

public enum DriveStatus {
    PENDING,    // AI-discovered, awaiting admin review — never shown publicly
    ACTIVE,
    CLOSED,
    UPCOMING,
    EXPIRED,
    CANCELLED
}