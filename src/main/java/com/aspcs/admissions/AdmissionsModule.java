package com.aspcs.admissions;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
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
@Table(name = "admission_inquiries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AdmissionInquiry {

    public enum Status { PENDING, CONTACTED, ADMITTED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_name", nullable = false)
    private String studentName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "grade_applying", nullable = false)
    private String gradeApplying;

    @Column(name = "parent_name", nullable = false)
    private String parentName;

    @Column(name = "parent_email", nullable = false)
    private String parentEmail;

    @Column(name = "parent_phone", nullable = false)
    private String parentPhone;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(name = "previous_school")
    private String previousSchool;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "submitted_at", updatable = false)
    private LocalDateTime submittedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  protected void onCreate() { submittedAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── DTO ─────────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class AdmissionDto {
    @NotBlank private String studentName;
    private LocalDate dateOfBirth;
    @NotBlank private String gradeApplying;
    @NotBlank private String parentName;
    @NotBlank @Email private String parentEmail;
    @NotBlank private String parentPhone;
    private String address;
    private String previousSchool;
    private String message;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface AdmissionRepository extends JpaRepository<AdmissionInquiry, UUID> {
    Page<AdmissionInquiry> findAllByOrderBySubmittedAtDesc(Pageable pageable);
    Page<AdmissionInquiry> findByStatusOrderBySubmittedAtDesc(AdmissionInquiry.Status status, Pageable pageable);
    long countByStatus(AdmissionInquiry.Status status);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class AdmissionsService {

    private final AdmissionRepository repo;

    public AdmissionInquiry submit(AdmissionDto dto) {
        AdmissionInquiry inquiry = AdmissionInquiry.builder()
                .studentName(dto.getStudentName())
                .dateOfBirth(dto.getDateOfBirth())
                .gradeApplying(dto.getGradeApplying())
                .parentName(dto.getParentName())
                .parentEmail(dto.getParentEmail())
                .parentPhone(dto.getParentPhone())
                .address(dto.getAddress())
                .previousSchool(dto.getPreviousSchool())
                .message(dto.getMessage())
                .status(AdmissionInquiry.Status.PENDING)
                .build();
        return repo.save(inquiry);
    }

    public Page<AdmissionInquiry> getAll(int page, int size, AdmissionInquiry.Status status) {
        Pageable p = PageRequest.of(page, size);
        return status != null
                ? repo.findByStatusOrderBySubmittedAtDesc(status, p)
                : repo.findAllByOrderBySubmittedAtDesc(p);
    }

    public AdmissionInquiry getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Inquiry not found"));
    }

    public AdmissionInquiry updateStatus(UUID id, AdmissionInquiry.Status status, String notes) {
        AdmissionInquiry inquiry = getById(id);
        inquiry.setStatus(status);
        inquiry.setAdminNotes(notes);
        return repo.save(inquiry);
    }

    public java.util.Map<String, Long> getStats() {
        return java.util.Map.of(
            "total",     repo.count(),
            "pending",   repo.countByStatus(AdmissionInquiry.Status.PENDING),
            "contacted", repo.countByStatus(AdmissionInquiry.Status.CONTACTED),
            "admitted",  repo.countByStatus(AdmissionInquiry.Status.ADMITTED)
        );
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/admissions")
@RequiredArgsConstructor
class AdmissionsController {

    private final AdmissionsService service;

    // Public
    @PostMapping
    public ResponseEntity<ApiResponse<AdmissionInquiry>> submit(
            @Valid @RequestBody AdmissionDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.submit(dto), "Inquiry submitted successfully"));
    }

    // Admin
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<AdmissionInquiry>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) AdmissionInquiry.Status status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, status)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getStats()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<AdmissionInquiry>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdmissionInquiry>> updateStatus(
            @PathVariable UUID id,
            @RequestParam AdmissionInquiry.Status status,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateStatus(id, status, notes), "Updated"));
    }
}
