package com.freshersdrive.entity;

import com.freshersdrive.enums.Role;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // Profile
    private String degree;
    private String branch;
    private Double cgpa;
    private Integer batchYear;
    private String college;
    private String phone;
    private String resumeUrl;

    // Preferences
    @Builder.Default
    @Column(columnDefinition = "boolean default true")
    private boolean emailAlerts = true;

    // Email Verification
    @Builder.Default
    @Column(name = "is_verified", nullable = false, columnDefinition = "boolean default false")
    private boolean emailVerified = false;

    private String verificationToken;

    private LocalDateTime verificationTokenExpiry;

    // Password Reset
    private String passwordResetToken;

    private LocalDateTime passwordResetTokenExpiry;

    @OneToMany(
            mappedBy = "user",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<UserApplication> applications;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}