package com.freshersdrive.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_subscriptions")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class AlertSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Preferences for alert filtering
    private String preferredCategory;   // IT_SOFTWARE, CORE_ENGINEERING, etc.
    private String preferredBranches;   // JSON array
    private Integer preferredBatch;

    @Column(nullable = false, columnDefinition = "boolean default true")
    private Boolean isActive = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}