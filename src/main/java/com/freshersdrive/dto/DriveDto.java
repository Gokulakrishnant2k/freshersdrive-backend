package com.freshersdrive.dto;

import com.freshersdrive.enums.DriveStatus;
import com.freshersdrive.enums.JobCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class DriveDto {

    @Data
    public static class Request {

        @NotBlank(message = "Company name is required")
        private String companyName;

        private String companyLogoUrl;
        private String companyDescription;
        private String companyWebsite;

        @NotBlank(message = "Job role is required")
        private String jobRole;

        private String jobDescription;

        @NotNull(message = "Category is required")
        private JobCategory category;

        private String jobType;
        private String location;
        private Boolean isRemote;

        private BigDecimal ctcMin;
        private BigDecimal ctcMax;
        private String ctcDisplay;
        private String stipend;

        private String eligibleDegrees;
        private String eligibleBranches;
        private Double minCgpa;
        private String eligibleBatches;
        private String experienceLevel;
        private Integer maxBacklogs;
        private String otherEligibilityCriteria;

        private String selectionProcess;
        private String selectionDetails;

        // The actual day of the campus/recruitment drive
        private LocalDate driveDate;

        @NotNull(message = "Deadline is required")
        private LocalDate deadline;

        private String applyLink;

        @NotNull(message = "Status is required")
        private DriveStatus status;

        private Boolean isFeatured;
        private Boolean autoDeleteEnabled;
    }

    @Data
    public static class Response {
        private Long id;
        private String companyName;
        private String companyLogoUrl;
        private String companyDescription;
        private String companyWebsite;
        private String jobRole;
        private String jobDescription;
        private JobCategory category;
        private String jobType;
        private String location;
        private Boolean isRemote;
        private BigDecimal ctcMin;
        private BigDecimal ctcMax;
        private String ctcDisplay;
        private String stipend;
        private String eligibleDegrees;
        private String eligibleBranches;
        private Double minCgpa;
        private String eligibleBatches;
        private String experienceLevel;
        private Integer maxBacklogs;
        private String otherEligibilityCriteria;
        private String selectionProcess;
        private String selectionDetails;
        private LocalDate driveDate;
        private LocalDate deadline;
        private String applyLink;
        private DriveStatus status;
        private Boolean isFeatured;
        private Boolean autoDeleteEnabled;
        private Integer viewCount;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    // Restored — referenced by EligibilityDto
    @Data
    public static class Summary {
        private Long id;
        private String companyName;
        private String companyLogoUrl;
        private String jobRole;
        private JobCategory category;
        private String location;
        private String ctcDisplay;
        private LocalDate driveDate;
        private LocalDate deadline;
        private DriveStatus status;
        private Boolean isFeatured;
        private String eligibleBranches;
        private String eligibleBatches;
        private Double minCgpa;
    }
}