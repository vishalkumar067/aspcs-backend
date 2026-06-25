package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// ─── Entity ────────────────────────────────────────────────────
@Entity
@Table(name = "progress_reports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class ProgressReport {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "assessment_id", nullable = false) private UUID assessmentId;
    @Column(name = "cycle_id", nullable = false)       private UUID cycleId;
    @Column(name = "student_id", nullable = false)     private UUID studentId;
    @Column(name = "pdf_url")    private String pdfUrl;
    @Column(name = "qr_code", nullable = false) private String qrCode;
    @Column(name = "generated_at") private LocalDateTime generatedAt;
    @Column(name = "generated_by") private UUID generatedBy;

    @PrePersist protected void onCreate() { generatedAt = LocalDateTime.now(); }
}

// ─── Repository ────────────────────────────────────────────────
interface ProgressReportRepository extends JpaRepository<ProgressReport, UUID> {
    Optional<ProgressReport> findByAssessmentId(UUID assessmentId);
    Optional<ProgressReport> findByQrCode(String qrCode);
    List<ProgressReport> findByStudentIdOrderByGeneratedAtDesc(UUID studentId);
    long countByCycleId(UUID cycleId);
}

// ─── Service ───────────────────────────────────────────────────
// Read-side only — writes happen inside ProgressReportService as part of
// the generate+dispatch flow. This service supports the Parent Portal /
// verification lookups and the "previous reports" history view.
@Service
@RequiredArgsConstructor
class ProgressReportLookupService {

    private final ProgressReportRepository repo;

    public List<ProgressReport> getHistoryForStudent(UUID studentId) {
        return repo.findByStudentIdOrderByGeneratedAtDesc(studentId);
    }

    // Powers the QR "Scan to verify this report" flow: anyone scanning the
    // code lands on a page that confirms the report is genuine and shows
    // basic non-sensitive metadata (which student, which cycle, when
    // generated) without requiring login.
    public ProgressReport verifyByQrCode(String qrCode) {
        return repo.findByQrCode(qrCode)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "No report found for this verification code"));
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/reports")
@RequiredArgsConstructor
class ProgressReportLookupController {

    private final ProgressReportLookupService service;

    @GetMapping("/student/{studentId}/history")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<List<ProgressReport>>> getHistory(@PathVariable UUID studentId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getHistoryForStudent(studentId)));
    }

    // Intentionally public (no @PreAuthorize) — this is the endpoint behind
    // the QR code printed on the PDF itself, meant to be scanned by anyone,
    // including parents with no ERP login at all.
    @GetMapping("/verify/{qrCode}")
    public ResponseEntity<ApiResponse<ProgressReport>> verify(@PathVariable String qrCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.verifyByQrCode(qrCode)));
    }
}
