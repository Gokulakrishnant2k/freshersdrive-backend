package com.freshersdrive.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

public class AuthRequest {

    @Data
    public static class Register {
        @NotBlank
        private String name;

        @NotBlank @Email
        private String email;

        @NotBlank @Size(min = 6, max = 40)
        private String password;

        private String degree;
        private String branch;
        private Double cgpa;
        private Integer batchYear;
        private String college;
        private String phone;
    }

    @Data
    public static class Login {
        @NotBlank @Email
        private String email;

        @NotBlank
        private String password;
    }

    @Data
    public static class ForgotPassword {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class ResetPassword {
        @NotBlank
        private String token;

        @NotBlank @Size(min = 6)
        private String newPassword;
    }
}