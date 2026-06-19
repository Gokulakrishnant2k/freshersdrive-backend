package com.freshersdrive.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_experiences")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class InterviewExperience {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "drive_id", nullable = false)
    private Drive drive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private String authorName;    // Can be anonymous

    @Column(columnDefinition = "TEXT")
    private String overallExperience;

    @Column(columnDefinition = "TEXT")
    private String round1;

    @Column(columnDefinition = "TEXT")
    private String round2;

    @Column(columnDefinition = "TEXT")
    private String round3;

    @Column(columnDefinition = "TEXT")
    private String hrRound;

    private String outcome;       // Selected / Rejected / Pending

    private Integer rating;       // 1-5

    private Boolean isApproved = false;

    @CreationTimestamp
    private LocalDateTime submittedAt;
}