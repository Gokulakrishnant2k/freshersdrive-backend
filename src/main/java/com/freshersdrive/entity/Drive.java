package com.freshersdrive.entity;

import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.JobCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "drives")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Drive {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =========================
    // COMPANY INFO
    // =========================
    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(length = 500)
    private String companyLogoUrl;

    @Column(length = 1000)
    private String companyDescription;

    private String companyWebsite;

    // =========================
    // DRIVE INFO
    // =========================
    @Column(nullable = false, length = 200)
    private String jobRole;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobCategory category;

    private String jobType; // Full-Time, Internship, Contract

    // =========================
    // LOCATION
    // =========================
    @Column(length = 500)
    private String location;

    private Boolean isRemote = false;

    // =========================
    // PACKAGE
    // =========================
    private BigDecimal ctcMin;

    private BigDecimal ctcMax;

    private String ctcDisplay;

    private String stipend;

    // =========================
    // ELIGIBILITY
    // =========================
    @Column(columnDefinition = "TEXT")
    private String eligibleDegrees;

    @Column(columnDefinition = "TEXT")
    private String eligibleBranches;

    private Double minCgpa;

    @Column(columnDefinition = "TEXT")
    private String eligibleBatches;

    // Freshers, 0-1 Years, 1-2 Years, etc.
    private String experienceLevel = "Freshers";

    private Integer maxBacklogs;

    private String otherEligibilityCriteria;

    // =========================
    // SELECTION PROCESS
    // =========================
    @Column(columnDefinition = "TEXT")
    private String selectionProcess;

    @Column(columnDefinition = "TEXT")
    private String selectionDetails;

    // =========================
    // APPLY DETAILS
    // =========================

    // The actual day of the campus/recruitment drive
    private LocalDate driveDate;

    @Column(nullable = false)
    private LocalDate deadline;

    private String applyLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriveStatus status;

    // =========================
    // AUTO DELETE
    // =========================
    private Boolean autoDeleteEnabled = false;

    // =========================
    // METADATA
    // =========================
    private Boolean isFeatured = false;

    private Integer viewCount = 0;

    @OneToMany(
            mappedBy = "drive",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<UserApplication> applications;

    @OneToMany(
            mappedBy = "drive",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<InterviewExperience> experiences;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}