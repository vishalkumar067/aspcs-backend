package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
    private final com.aspcs.student.StudentRepository studentRepo;
    private final ReportingCycleRepository cycleRepo;

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

    // Proxies the PDF through our own backend rather than redirecting to the
    // raw Cloudinary URL, for two reasons: (1) PDFs are uploaded as
    // resource_type=raw, and Cloudinary's fl_attachment custom-filename
    // flag doesn't support raw assets the normal way — it needs a
    // different URL path and creates a duplicate stored copy, which isn't
    // worth the storage cost just to control a filename; (2) this way the
    // download is gated by the same @PreAuthorize as everything else,
    // instead of being a permanently-public Cloudinary link.
    public DownloadablePdf downloadPdf(UUID reportId) throws java.io.IOException {
        ProgressReport report = repo.findById(reportId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Report not found"));
        if (report.getPdfUrl() == null) {
            throw new IllegalStateException("This report has no PDF file recorded");
        }

        var student = studentRepo.findById(report.getStudentId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Student not found"));
        var cycle = cycleRepo.findById(report.getCycleId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Reporting cycle not found"));

        byte[] bytes = fetchBytes(report.getPdfUrl());
        String filename = buildFilename(student.getFullName(), cycle.getName());
        return new DownloadablePdf(bytes, filename);
    }

    // "Rahul Kumar" + "Cycle 01 - July 2026" -> "Rahul_Kumar_Cycle_01_July_2026.pdf"
    // Strips anything that's unsafe in a filename on Windows/macOS/Linux
    // (most punctuation, slashes) rather than just spaces, since cycle
    // names may contain dashes or other characters a teacher typed freely.
    private String buildFilename(String studentName, String cycleName) {
        String combined = studentName + "_" + cycleName;
        String safe = combined.replaceAll("[^a-zA-Z0-9_\\- ]", "").trim().replaceAll("\\s+", "_");
        return safe + ".pdf";
    }

    private byte[] fetchBytes(String url) throws java.io.IOException {
        java.net.URLConnection conn = new java.net.URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try (var in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }
}

record DownloadablePdf(byte[] bytes, String filename) {}

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

    // Downloads the generated PDF, named "{Student Name}_{Cycle Name}.pdf".
    // Same roles as report generation itself — anyone who can generate a
    // report can also download it.
    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<byte[]> download(@PathVariable UUID reportId) throws java.io.IOException {
        DownloadablePdf pdf = service.downloadPdf(reportId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(
                org.springframework.http.ContentDisposition.attachment()
                        .filename(pdf.filename(), java.nio.charset.StandardCharsets.UTF_8)
                        .build());
        return new ResponseEntity<>(pdf.bytes(), headers, org.springframework.http.HttpStatus.OK);
    }

    // Intentionally public (no @PreAuthorize) — this is the endpoint behind
    // the QR code printed on the PDF itself, meant to be scanned by anyone,
    // including parents with no ERP login at all.
    @GetMapping("/verify/{qrCode}")
    public ResponseEntity<ApiResponse<ProgressReport>> verify(@PathVariable String qrCode) {
        return ResponseEntity.ok(ApiResponse.ok(service.verifyByQrCode(qrCode)));
    }
}
