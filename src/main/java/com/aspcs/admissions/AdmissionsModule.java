package com.aspcs.admissions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.aspcs.common.ApiResponse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "admission_inquiries")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class AdmissionInquiry {

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
	@Column(nullable = false)
	private Status status;

	@Column(name = "admin_notes", columnDefinition = "TEXT")
	private String adminNotes;

	@Column(name = "submitted_at", updatable = false)
	private LocalDateTime submittedAt;

	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		submittedAt = updatedAt = LocalDateTime.now();
		if (status == null)
			status = Status.PENDING;
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}

	enum Status {
		PENDING, CONTACTED, SHORTLISTED, ADMITTED, REJECTED
	}
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
class SubmitInquiryRequest {
	@NotBlank(message = "Student name is required")
	public String studentName;
	public LocalDate dateOfBirth;
	@NotBlank(message = "Grade is required")
	public String gradeApplying;
	@NotBlank(message = "Parent name is required")
	public String parentName;
	@NotBlank(message = "Email is required")
	@Email(message = "Invalid email address")
	public String parentEmail;
	@NotBlank(message = "Phone is required")
	public String parentPhone;
	public String address;
	public String previousSchool;
	public String message;
}

class UpdateStatusRequest {
	@NotNull
	public AdmissionInquiry.Status status;
	public String adminNotes;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface AdmissionInquiryRepository extends JpaRepository<AdmissionInquiry, UUID> {
	Page<AdmissionInquiry> findByStatusOrderBySubmittedAtDesc(AdmissionInquiry.Status status, Pageable pageable);

	Page<AdmissionInquiry> findAllByOrderBySubmittedAtDesc(Pageable pageable);

	long countByStatus(AdmissionInquiry.Status status);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class AdmissionsService {

	private final AdmissionInquiryRepository repo;

	public AdmissionInquiry submit(SubmitInquiryRequest req) {
		AdmissionInquiry inquiry = AdmissionInquiry.builder().studentName(req.studentName).dateOfBirth(req.dateOfBirth)
				.gradeApplying(req.gradeApplying).parentName(req.parentName).parentEmail(req.parentEmail)
				.parentPhone(req.parentPhone).address(req.address).previousSchool(req.previousSchool)
				.message(req.message).status(AdmissionInquiry.Status.PENDING).build();
		return repo.save(inquiry);
	}

	public Page<AdmissionInquiry> getAll(int page, int size, String status) {
		Pageable pageable = PageRequest.of(page, size);
		if (status != null && !status.isBlank()) {
			return repo.findByStatusOrderBySubmittedAtDesc(AdmissionInquiry.Status.valueOf(status.toUpperCase()),
					pageable);
		}
		return repo.findAllByOrderBySubmittedAtDesc(pageable);
	}

	public AdmissionInquiry getById(UUID id) {
		return repo.findById(id)
				.orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Inquiry not found: " + id));
	}

	public AdmissionInquiry updateStatus(UUID id, UpdateStatusRequest req) {
		AdmissionInquiry inquiry = getById(id);
		inquiry.setStatus(req.status);
		if (req.adminNotes != null)
			inquiry.setAdminNotes(req.adminNotes);
		return repo.save(inquiry);
	}

	public void delete(UUID id) {
		repo.deleteById(id);
	}
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/admissions")
@RequiredArgsConstructor
class AdmissionsController {

	private final AdmissionsService service;

	// Public — form submission
	@PostMapping("/inquiries")
	public ResponseEntity<ApiResponse<AdmissionInquiry>> submit(@Valid @RequestBody SubmitInquiryRequest req) {
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.ok(service.submit(req), "Application submitted successfully"));
	}

	// Admin only
	@GetMapping("/inquiries")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
	public ResponseEntity<ApiResponse<Page<AdmissionInquiry>>> getAll(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String status) {
		return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, status)));
	}

	@GetMapping("/inquiries/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
	public ResponseEntity<ApiResponse<AdmissionInquiry>> getById(@PathVariable UUID id) {
		return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
	}

	@PatchMapping("/inquiries/{id}/status")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public ResponseEntity<ApiResponse<AdmissionInquiry>> updateStatus(@PathVariable UUID id,
			@Valid @RequestBody UpdateStatusRequest req) {
		return ResponseEntity.ok(ApiResponse.ok(service.updateStatus(id, req), "Status updated"));
	}

	@DeleteMapping("/inquiries/{id}")
	@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
	public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
		service.delete(id);
		return ResponseEntity.ok(ApiResponse.ok(null, "Inquiry deleted"));
	}
}
