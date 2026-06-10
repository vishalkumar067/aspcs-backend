package com.aspcs.tc;

import com.aspcs.common.ApiResponse;
import com.aspcs.student.StudentModule;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "tc_requests")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
class TcRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admission_no", nullable = false)
    private String admissionNo;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "class_studying")
    private String classStudying;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "applicant_name", nullable = false)
    private String applicantName;

    @Column(name = "applicant_phone", nullable = false)
    private String applicantPhone;

    @Column(name = "applicant_email")
    private String applicantEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TcStatus status;

    @Column(name = "tc_number", unique = true)
    private String tcNumber;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "admin_remarks", columnDefinition = "TEXT")
    private String adminRemarks;

    @Column(name = "requested_at", updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        requestedAt = updatedAt = LocalDateTime.now();
        if (status == null) status = TcStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    enum TcStatus { PENDING, APPROVED, ISSUED, REJECTED }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
class TcRequestDTO {
    @NotBlank public String admissionNo;
    @NotBlank public String applicantName;
    @NotBlank public String applicantPhone;
    public String applicantEmail;
    public String reason;
}

class UpdateTcRequest {
    public TcRequest.TcStatus status;
    public String adminRemarks;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface TcRequestRepository extends JpaRepository<TcRequest, UUID> {
    Page<TcRequest> findByStatusOrderByRequestedAtDesc(TcRequest.TcStatus status, Pageable pageable);
    Page<TcRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    boolean existsByAdmissionNoAndStatusIn(String admissionNo, java.util.List<TcRequest.TcStatus> statuses);
    long countByStatus(TcRequest.TcStatus status);
}

// ─── TC PDF Generator ────────────────────────────────────────────────────────
@Service
class TcPdfService {

    public byte[] generateTcPdf(TcRequest tc) {
        // Using simple HTML-based approach for PDF generation
        // Replace with iText or JasperReports for production-grade PDFs
        StringBuilder html = new StringBuilder();
        html.append("""
            <!DOCTYPE html>
            <html>
            <head>
            <style>
              body { font-family: 'Times New Roman', serif; margin: 40px; color: #000; }
              .header { text-align: center; border-bottom: 3px double #000; padding-bottom: 15px; margin-bottom: 20px; }
              .school-name { font-size: 24px; font-weight: bold; text-transform: uppercase; letter-spacing: 2px; }
              .school-sub { font-size: 13px; margin-top: 4px; }
              .tc-title { text-align: center; font-size: 20px; font-weight: bold; text-decoration: underline; margin: 20px 0; text-transform: uppercase; letter-spacing: 3px; }
              .tc-no { text-align: right; font-size: 13px; margin-bottom: 15px; }
              table { width: 100%; border-collapse: collapse; margin: 10px 0; }
              td { padding: 8px 5px; font-size: 13px; vertical-align: top; }
              td:first-child { width: 45%; font-weight: bold; }
              .footer { margin-top: 50px; display: flex; justify-content: space-between; }
              .sign { text-align: center; }
              .watermark { color: #28a745; font-weight: bold; font-size: 14px; text-align: center; margin-top: 20px; }
              .border-box { border: 2px solid #000; padding: 20px; }
            </style>
            </head>
            <body>
            """);

        html.append("<div class='border-box'>");
        html.append("<div class='header'>");
        html.append("<div class='school-name'>Acharya Shri Sudarshan Patna Central School</div>");
        html.append("<div class='school-sub'>Sudarshan Vihar, New Bypass Road, Jaganpura, Patna - 800027</div>");
        html.append("<div class='school-sub'>Ph: +91-91029 97549 | Email: info@aspcspatna.ac.in</div>");
        html.append("<div class='school-sub'>Affiliation No: 330015 | School Code: 65016</div>");
        html.append("</div>");

        html.append("<div class='tc-title'>Transfer Certificate</div>");
        html.append("<div class='tc-no'>TC No: <strong>").append(tc.getTcNumber()).append("</strong> &nbsp;&nbsp; Date: <strong>")
            .append(tc.getIssueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))).append("</strong></div>");

        html.append("<table>");
        appendRow(html, "1. Name of the Student", tc.getStudentName().toUpperCase());
        appendRow(html, "2. Admission No.", tc.getAdmissionNo());
        appendRow(html, "3. Class in which studying", tc.getClassStudying());
        appendRow(html, "4. Date of Issue", tc.getIssueDate().format(DateTimeFormatter.ofPattern("dd MMMM yyyy")));
        appendRow(html, "5. Reason for Leaving", tc.getReason() != null ? tc.getReason() : "Parent's Request");
        appendRow(html, "6. Whether failed", "No");
        appendRow(html, "7. Conduct & Character", "Good");
        appendRow(html, "8. Dues cleared", "Yes");
        html.append("</table>");

        html.append("<div class='watermark'>*** ISSUED WITH GOOD CONDUCT ***</div>");

        html.append("<div class='footer'>");
        html.append("<div class='sign'><br><br>________________________<br>Class Teacher</div>");
        html.append("<div class='sign'><br><br>________________________<br>Office Seal</div>");
        html.append("<div class='sign'><br><br>________________________<br>Principal</div>");
        html.append("</div>");
        html.append("</div></body></html>");

        // Return HTML as bytes — frontend can print/save as PDF
        return html.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder html, String label, String value) {
        html.append("<tr><td>").append(label).append("</td><td>: ").append(value != null ? value : "—").append("</td></tr>");
    }
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class TcService {

    private final TcRequestRepository  repo;
    private final com.aspcs.student.StudentModule.StudentRepository studentRepo;
    private final TcPdfService         pdfService;

    public TcRequest submitRequest(TcRequestDTO dto) {
        // Verify student exists
        studentRepo.findByAdmissionNo(dto.admissionNo)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "No student found with admission number: " + dto.admissionNo +
                        ". Please contact the school office."));

        // Check no pending request
        if (repo.existsByAdmissionNoAndStatusIn(dto.admissionNo,
                java.util.List.of(TcRequest.TcStatus.PENDING, TcRequest.TcStatus.APPROVED))) {
            throw new IllegalArgumentException("A TC request for this student is already in progress");
        }

        var student = studentRepo.findByAdmissionNo(dto.admissionNo).orElseThrow();

        TcRequest request = TcRequest.builder()
                .admissionNo(dto.admissionNo)
                .studentName(student.getFullName())
                .classStudying(student.getCurrentClass() +
                        (student.getSection() != null ? " - " + student.getSection() : ""))
                .reason(dto.reason)
                .applicantName(dto.applicantName)
                .applicantPhone(dto.applicantPhone)
                .applicantEmail(dto.applicantEmail)
                .status(TcRequest.TcStatus.PENDING)
                .build();

        return repo.save(request);
    }

    public Page<TcRequest> getAll(int page, int size, String status) {
        Pageable pageable = PageRequest.of(page, size);
        if (status != null && !status.isBlank())
            return repo.findByStatusOrderByRequestedAtDesc(
                    TcRequest.TcStatus.valueOf(status), pageable);
        return repo.findAllByOrderByRequestedAtDesc(pageable);
    }

    public TcRequest getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("TC request not found"));
    }

    public TcRequest updateStatus(UUID id, UpdateTcRequest req) {
        TcRequest tc = getById(id);
        tc.setStatus(req.status);
        tc.setAdminRemarks(req.adminRemarks);

        if (req.status == TcRequest.TcStatus.ISSUED) {
            tc.setTcNumber(generateTcNumber());
            tc.setIssueDate(LocalDate.now());
        }

        return repo.save(tc);
    }

    public byte[] generatePdf(UUID id) {
        TcRequest tc = getById(id);
        if (tc.getStatus() != TcRequest.TcStatus.ISSUED)
            throw new IllegalArgumentException("TC must be issued before generating PDF");
        return pdfService.generateTcPdf(tc);
    }

    private String generateTcNumber() {
        return "ASPCS/TC/" + LocalDate.now().getYear() + "/" +
                String.format("%04d", repo.count() + 1);
    }

    public java.util.Map<String, Long> getStats() {
        return java.util.Map.of(
                "pending",  repo.countByStatus(TcRequest.TcStatus.PENDING),
                "approved", repo.countByStatus(TcRequest.TcStatus.APPROVED),
                "issued",   repo.countByStatus(TcRequest.TcStatus.ISSUED),
                "rejected", repo.countByStatus(TcRequest.TcStatus.REJECTED)
        );
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/tc")
@RequiredArgsConstructor
class TcController {

    private final TcService service;

    // Public — submit TC request
    @PostMapping("/request")
    public ResponseEntity<ApiResponse<TcRequest>> submitRequest(
            @RequestBody TcRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.submitRequest(dto),
                        "TC request submitted successfully. We will contact you within 3-5 working days."));
    }

    // Admin
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<TcRequest>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, status)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getStats()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TcRequest>> updateStatus(
            @PathVariable UUID id,
            @RequestBody UpdateTcRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateStatus(id, req), "TC status updated"));
    }

    // Generate & download TC PDF
    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> generatePdf(@PathVariable UUID id) {
        byte[] pdf = service.generatePdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_HTML_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=TC_" + id + ".html")
                .body(pdf);
    }
}
