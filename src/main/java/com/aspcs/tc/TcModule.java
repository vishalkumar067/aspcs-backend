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
import java.util.Optional;
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "tc_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class TcRequest {

    public enum TcStatus { PENDING, APPROVED, REJECTED, ISSUED }

    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "admission_no",  nullable = false) private String admissionNo;
    @Column(name = "student_name",  nullable = false) private String studentName;
    @Column(name = "class_studying")                  private String classStudying;
    @Column(name = "date_of_birth")                   private LocalDate dateOfBirth;  // for verification
    @Column(columnDefinition = "TEXT")                private String reason;

    @Column(name = "applicant_name",  nullable = false) private String applicantName;
    @Column(name = "applicant_phone", nullable = false) private String applicantPhone;
    @Column(name = "applicant_email")                   private String applicantEmail;

    @Enumerated(EnumType.STRING)
    private TcStatus status = TcStatus.PENDING;

    @Column(name = "tc_number", unique = true) private String tcNumber;
    @Column(name = "issue_date")               private LocalDate issueDate;
    @Column(name = "admin_remarks", columnDefinition = "TEXT") private String adminRemarks;

    @Column(name = "requested_at", updatable = false) private LocalDateTime requestedAt;
    @Column(name = "updated_at")                      private LocalDateTime updatedAt;

    @PrePersist  protected void onCreate() { requestedAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class TcRequestDto {
    @NotBlank private String admissionNo;
    @NotBlank private String studentName;
    private String    classStudying;
    private LocalDate dateOfBirth;
    private String    reason;
    @NotBlank private String applicantName;
    @NotBlank private String applicantPhone;
    private String applicantEmail;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class TcUpdateDto {
    private TcRequest.TcStatus status;
    private String    adminRemarks;
    private String    tcNumber;
    private LocalDate issueDate;
}

// Public verification response — only safe fields
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class TcVerifyResponse {
    private String    tcNumber;
    private String    studentName;
    private String    admissionNo;
    private String    classStudying;
    private LocalDate issueDate;
    private String    status;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface TcRequestRepository extends JpaRepository<TcRequest, UUID> {
    Page<TcRequest> findAllByOrderByRequestedAtDesc(Pageable p);
    Page<TcRequest> findByStatusOrderByRequestedAtDesc(TcRequest.TcStatus status, Pageable p);
    Optional<TcRequest> findByTcNumber(String tcNumber);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@org.springframework.stereotype.Service
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
        TcRequest tc = new TcRequest();
        tc.setAdmissionNo(dto.getAdmissionNo());
        tc.setStudentName(dto.getStudentName());
        tc.setClassStudying(dto.getClassStudying());
        tc.setDateOfBirth(dto.getDateOfBirth());
        tc.setReason(dto.getReason());
        tc.setApplicantName(dto.getApplicantName());
        tc.setApplicantPhone(dto.getApplicantPhone());
        tc.setApplicantEmail(dto.getApplicantEmail());
        tc.setStatus(TcRequest.TcStatus.PENDING);
        return repo.save(tc);
    }

    public TcRequest updateStatus(UUID id, TcUpdateDto dto) {
        TcRequest tc = getById(id);
        tc.setStatus(dto.getStatus());
        tc.setAdminRemarks(dto.getAdminRemarks());
        if (dto.getStatus() == TcRequest.TcStatus.ISSUED) {
            if (dto.getTcNumber() != null) tc.setTcNumber(dto.getTcNumber());
            tc.setIssueDate(dto.getIssueDate() != null ? dto.getIssueDate() : LocalDate.now());
        }
        return repo.save(tc);
    }

    /** Public TC verification — TC number + date of birth must match */
    public TcVerifyResponse verify(String tcNumber, LocalDate dateOfBirth) {
        TcRequest tc = repo.findByTcNumber(tcNumber)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("TC not found"));

        if (tc.getStatus() != TcRequest.TcStatus.ISSUED) {
            throw new IllegalStateException("This TC has not been issued yet");
        }

        // Verify date of birth if stored
        if (tc.getDateOfBirth() != null && !tc.getDateOfBirth().equals(dateOfBirth)) {
            throw new IllegalArgumentException("Details do not match our records");
        }

        return new TcVerifyResponse(
            tc.getTcNumber(),
            tc.getStudentName(),
            tc.getAdmissionNo(),
            tc.getClassStudying(),
            tc.getIssueDate(),
            tc.getStatus().name()
        );
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequiredArgsConstructor
class TcController {

    private final TcService service;

    // ── Public ───────────────────────────────────────────────────────────────

    /** Submit TC request */
    @PostMapping("/tc/submit")
    public ResponseEntity<ApiResponse<TcRequest>> submit(@Valid @RequestBody TcRequestDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.submit(dto), "TC request submitted successfully"));
    }

    /** Verify TC by number + date of birth */
    @GetMapping("/tc/verify")
    public ResponseEntity<ApiResponse<TcVerifyResponse>> verify(
            @RequestParam String tcNumber,
            @RequestParam @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate dateOfBirth) {
        return ResponseEntity.ok(ApiResponse.ok(service.verify(tcNumber, dateOfBirth), "TC verified"));
    }

    // ── Admin ─────────────────────────────────────────────────────────────────

    @GetMapping("/tc")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<TcRequest>>> getAll(
            @RequestParam(defaultValue = "0")  int page,
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
}
