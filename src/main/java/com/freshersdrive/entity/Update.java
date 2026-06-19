package com.freshersdrive.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "updates") // ✅ FIXED: "update" is a reserved MySQL keyword
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Update {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;

    @Column(length = 2000)
    private String message;

    private String tag;

    private LocalDateTime createdAt;
}