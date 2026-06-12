package com.aspcs.tc;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
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
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "tc_requests")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class TcRequest {

    public enum TcStatus { PENDING, APPROVED, REJECTED, ISSUED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "admission_no", nullable = false)
    private String admissionNo;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "class_studying")
    private String classStudying;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "applicant_name", nullable = false)
    private String applicantName;

    @Column(name = "applicant_phone", nullable = false)
    private String applicantPhone;

    @Column(name = "applicant_email")
    private String applicantEmail;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private TcStatus status = TcStatus.PENDING;

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

    @PrePersist  protected void onCreate() { requestedAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class TcRequestDto {
    @NotBlank private String admissionNo;
    @NotBlank private String studentName;
    private String classStudying;
    private String reason;
    @NotBlank private String applicantName;
    @NotBlank private String applicantPhone;
    private String applicantEmail;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class TcUpdateDto {
    private TcRequest.TcStatus status;
    private String adminRemarks;
    private String tcNumber;
    private LocalDate issueDate;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface TcRequestRepository extends JpaRepository<TcRequest, UUID> {
    Page<TcRequest> findAllByOrderByRequestedAtDesc(Pageable pageable);
    Page<TcRequest> findByStatusOrderByRequestedAtDesc(TcRequest.TcStatus status, Pageable pageable);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class TcService {

    private final TcRequestRepository repo;

    public Page<TcRequest> getAll(int page, int size, TcRequest.TcStatus status) {
        Pageable p = PageRequest.of(page, size);
        return status != null
                ? repo.findByStatusOrderByRequestedAtDesc(status, p)
                : repo.findAllByOrderByRequestedAtDesc(p);
    }

    public TcRequest getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("TC request not found"));
    }

    public TcRequest submit(TcRequestDto dto) {
        TcRequest tc = TcRequest.builder()
                .admissionNo(dto.getAdmissionNo())
                .studentName(dto.getStudentName())
                .classStudying(dto.getClassStudying())
                .reason(dto.getReason())
                .applicantName(dto.getApplicantName())
                .applicantPhone(dto.getApplicantPhone())
                .applicantEmail(dto.getApplicantEmail())
                .status(TcRequest.TcStatus.PENDING)
                .build();
        return repo.save(tc);
    }

    public TcRequest updateStatus(UUID id, TcUpdateDto dto) {
        TcRequest tc = getById(id);
        tc.setStatus(dto.getStatus());
        tc.setAdminRemarks(dto.getAdminRemarks());

        if (dto.getStatus() == TcRequest.TcStatus.ISSUED) {
            if (dto.getTcNumber() != null) tc.setTcNumber(dto.getTcNumber());
            if (dto.getIssueDate()  != null) tc.setIssueDate(dto.getIssueDate());
        }

        return repo.save(tc);
    }

    // Generate TC certificate content
    public String generateCertificate(UUID id) {
        TcRequest tc = getById(id);
        return String.format("""
            TRANSFER CERTIFICATE
            TC No: %s   Date: %s
            Student Name:  %s
            Admission No:  %s
            Class:         %s
            Reason:        %s
            """,
                tc.getTcNumber(),
                tc.getIssueDate(),
                tc.getStudentName(),
                tc.getAdmissionNo(),
                tc.getClassStudying(),
                tc.getReason() != null ? tc.getReason() : "-");
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequiredArgsConstructor
class TcController {

    private final TcService service;

    // Public - submit request
    @PostMapping("/tc/submit")
    public ResponseEntity<ApiResponse<TcRequest>> submit(@Valid @RequestBody TcRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.submit(dto), "TC request submitted successfully"));
    }

    // Admin
    @GetMapping("/tc")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<TcRequest>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) TcRequest.TcStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, status)));
    }

    @GetMapping("/tc/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<TcRequest>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PatchMapping("/tc/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TcRequest>> updateStatus(
            @PathVariable UUID id, @RequestBody TcUpdateDto dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateStatus(id, dto), "Status updated"));
    }

    @GetMapping("/tc/{id}/certificate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<String> getCertificate(@PathVariable UUID id) {
        return ResponseEntity.ok(service.generateCertificate(id));
    }
}
