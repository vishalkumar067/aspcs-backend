package com.aspcs.academic;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

// ─── Subject ─────────────────────────────────────────────────
@Entity @Table(name = "subjects")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Subject {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String code;
    @Column(name = "max_marks") private int maxMarks = 100;
    @Column(name = "pass_marks") private int passMarks = 33;
    @Column(name = "is_practical") private boolean practical;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
