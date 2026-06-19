package com.freshersdrive.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "drive_registrations",
    uniqueConstraints = @UniqueConstraint(columnNames = {"drive_id", "student_email"})
)
@Data
public class DriveRegistration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Links to your existing Drive entity
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drive_id", nullable = false)
    private Drive drive;

    @Column(nullable = false)
    private String studentEmail;

    @Column(nullable = false)
    private String studentName;

    @Column(updatable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();
}