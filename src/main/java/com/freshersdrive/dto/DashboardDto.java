package com.freshersdrive.dto;

import com.freshersdrive.enums.ApplicationStatus;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DashboardDto {
    private String userName;
    private String degree;
    private String branch;
    private Double cgpa;
    private Integer batchYear;

    // Stats
    private long totalSaved;
    private long totalApplied;
    private long interviewScheduled;
    private long selected;
    private long rejected;

    // Recent activity
    private List<ApplicationSummary> recentApplications;

    @Data
    public static class ApplicationSummary {
        private Long applicationId;
        private Long driveId;
        private String companyName;
        private String jobRole;
        private ApplicationStatus status;
        private String appliedAt;
    }
}