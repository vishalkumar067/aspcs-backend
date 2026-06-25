package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

// ─── Entity ────────────────────────────────────────────────────
@Entity
@Table(name = "assessment_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class AssessmentSettings {
    @Id private short id = 1;
    @Column(name = "performance_mode", nullable = false) private String performanceMode = "RATING"; // MARKS or RATING
    @Column(name = "attendance_alert_pct", nullable = false) private BigDecimal attendanceAlertPct = new BigDecimal("75.00");
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist @PreUpdate protected void onSave() { updatedAt = LocalDateTime.now(); }
}

// ─── DTO ───────────────────────────────────────────────────────
class AssessmentSettingsRequest {
    @NotBlank public String performanceMode; // MARKS or RATING
    public BigDecimal attendanceAlertPct;
}

// ─── Repository ────────────────────────────────────────────────
interface AssessmentSettingsRepository extends JpaRepository<AssessmentSettings, Short> {}

// ─── Service ───────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class AssessmentSettingsService {

    private final AssessmentSettingsRepository repo;

    public AssessmentSettings get() {
        return repo.findById((short) 1).orElseGet(() -> {
            AssessmentSettings s = new AssessmentSettings();
            return repo.save(s);
        });
    }

    public AssessmentSettings update(AssessmentSettingsRequest req) {
        String mode = req.performanceMode.toUpperCase();
        if (!mode.equals("MARKS") && !mode.equals("RATING")) {
            throw new IllegalArgumentException("performanceMode must be MARKS or RATING");
        }
        AssessmentSettings s = get();
        s.setPerformanceMode(mode);
        if (req.attendanceAlertPct != null) {
            s.setAttendanceAlertPct(req.attendanceAlertPct);
        }
        return repo.save(s);
    }

    public boolean isMarksMode() {
        return "MARKS".equals(get().getPerformanceMode());
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/settings")
@RequiredArgsConstructor
class AssessmentSettingsController {

    private final AssessmentSettingsService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<AssessmentSettings>> get() {
        return ResponseEntity.ok(ApiResponse.ok(service.get()));
    }

    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AssessmentSettings>> update(
            @RequestBody AssessmentSettingsRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(req), "Settings updated"));
    }
}
