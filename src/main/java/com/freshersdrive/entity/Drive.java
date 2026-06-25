package com.freshersdrive.entity;

import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.DriveSource;
import com.freshersdrive.enums.ReviewStatus;
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

    @Column(nullable = false, length = 200)
    private String companyName;

    @Column(length = 500)
    private String companyLogoUrl;

    @Column(length = 1000)
    private String companyDescription;

    private String companyWebsite;

    @Column(nullable = false, length = 200)
    private String jobRole;

    @Column(columnDefinition = "TEXT")
    private String jobDescription;

    @Column(columnDefinition = "TEXT")
    private String keySkills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobCategory category;

    private String jobType;

    @Column(length = 500)
    private String location;

    @Builder.Default
    private Boolean isRemote = false;

    private BigDecimal ctcMin;

    private BigDecimal ctcMax;

    private String ctcDisplay;

    private String stipend;

    @Column(columnDefinition = "TEXT")
    private String eligibleDegrees;

    @Column(columnDefinition = "TEXT")
    private String eligibleBranches;

    private Double minCgpa;

    @Column(columnDefinition = "TEXT")
    private String eligibleBatches;

    @Builder.Default
    private String experienceLevel = "Freshers";

    private Integer maxBacklogs;

    private String otherEligibilityCriteria;

    @Column(columnDefinition = "TEXT")
    private String selectionProcess;

    @Column(columnDefinition = "TEXT")
    private String selectionDetails;

    private LocalDate driveDate;

    @Column(nullable = false)
    private LocalDate deadline;

    private String applyLink;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DriveStatus status;

    @Builder.Default
    private Boolean autoDeleteEnabled = false;

    @Builder.Default
    private Boolean isFeatured = false;
    
    @Builder.Default
    private Boolean isHighlighted = false;

    @Builder.Default
    private Integer viewCount = 0;

    // =========================
    // AI DISCOVERY SUPPORT
    // =========================

    @Column(unique = true, length = 64)
    private String fingerprint;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private DriveSource source = DriveSource.MANUAL;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ReviewStatus reviewStatus = ReviewStatus.PENDING_REVIEW;

    @Builder.Default
    private Boolean deadlineGuessed = false;

    // Auto-delete drive 30 days after deadline — default ON
    @Builder.Default
    private Boolean autoExpireAfter30Days = true;

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