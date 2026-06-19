package com.freshersdrive.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class EligibilityDto {

    @Data
    public static class CheckRequest {
        @NotBlank
        private String degree;      // B.E, B.Tech

        @NotBlank
        private String branch;      // EEE, CSE, ECE

        @NotNull
        private Double cgpa;

        @NotNull
        private Integer batchYear;
    }

    @Data
    public static class CheckResponse {
        private String degree;
        private String branch;
        private Double cgpa;
        private Integer batchYear;
        private int totalEligible;
        private List<DriveDto.Summary> eligibleDrives;
    }
}