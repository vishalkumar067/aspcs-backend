package com.aspcs.career;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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

// ─── JobListing Entity ───────────────────────────────────────────────────────
@Entity
@Table(name = "job_listings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String department;

    @Builder.Default
    private String type = "FULL_TIME";

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String requirements;

    @Column(columnDefinition = "TEXT")
    private String responsibilities;

    private String experience;
    private String qualification;
    private String salary;

    @Column(name = "last_date")
    private LocalDate lastDate;

    @Builder.Default
    private int vacancies = 1;

    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── JobApplication Entity ───────────────────────────────────────────────────
@Entity
@Table(name = "job_applications")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class JobApplication {

    public enum AppStatus { APPLIED, SHORTLISTED, INTERVIEW, SELECTED, REJECTED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id")
    private UUID jobId;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;
    private String qualification;
    private String experience;

    @Column(name = "cover_letter", columnDefinition = "TEXT")
    private String coverLetter;

    @Column(name = "resume_url")
    private String resumeUrl;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AppStatus status = AppStatus.APPLIED;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "applied_at", updatable = false)
    private LocalDateTime appliedAt;

    @PrePersist protected void onCreate() { appliedAt = LocalDateTime.now(); }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class JobListingDto {
    @NotBlank private String title;
    @NotBlank private String department;
    private String type;
    @NotBlank private String description;
    private String requirements;
    private String responsibilities;
    private String experience;
    private String qualification;
    private String salary;
    private LocalDate lastDate;
    private int vacancies;
    private boolean active;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class JobApplicationDto {
    @NotBlank private String name;
    @NotBlank @Email private String email;
    private String phone;
    private String qualification;
    private String experience;
    private String coverLetter;
    private String resumeUrl;
}

// ─── Repositories ────────────────────────────────────────────────────────────
interface JobListingRepository extends JpaRepository<JobListing, UUID> {
    Page<JobListing> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<JobListing> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    Page<JobApplication> findByJobIdOrderByAppliedAtDesc(UUID jobId, Pageable pageable);
    Page<JobApplication> findAllByOrderByAppliedAtDesc(Pageable pageable);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class CareerService {

    private final JobListingRepository    jobRepo;
    private final JobApplicationRepository appRepo;

    public Page<JobListing> getActiveJobs(int page, int size) {
        return jobRepo.findByActiveTrueOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<JobListing> getAllJobs(int page, int size) {
        return jobRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public JobListing getJobById(UUID id) {
        return jobRepo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Job not found"));
    }

    public JobListing createJob(JobListingDto dto) {
        JobListing job = JobListing.builder()
                .title(dto.getTitle())
                .department(dto.getDepartment())
                .type(dto.getType() != null ? dto.getType() : "FULL_TIME")
                .description(dto.getDescription())
                .requirements(dto.getRequirements())
                .responsibilities(dto.getResponsibilities())
                .experience(dto.getExperience())
                .qualification(dto.getQualification())
                .salary(dto.getSalary())
                .lastDate(dto.getLastDate())
                .vacancies(dto.getVacancies() > 0 ? dto.getVacancies() : 1)
                .active(dto.isActive())
                .build();
        return jobRepo.save(job);
    }

    public JobListing updateJob(UUID id, JobListingDto dto) {
        JobListing job = getJobById(id);
        job.setTitle(dto.getTitle());
        job.setDepartment(dto.getDepartment());
        job.setDescription(dto.getDescription());
        job.setRequirements(dto.getRequirements());
        job.setResponsibilities(dto.getResponsibilities());
        job.setExperience(dto.getExperience());
        job.setQualification(dto.getQualification());
        job.setSalary(dto.getSalary());
        job.setLastDate(dto.getLastDate());
        job.setVacancies(dto.getVacancies() > 0 ? dto.getVacancies() : job.getVacancies());
        job.setActive(dto.isActive());
        return jobRepo.save(job);
    }

    public JobListing toggleActive(UUID id) {
        JobListing job = getJobById(id);
        job.setActive(!job.isActive());
        return jobRepo.save(job);
    }

    public void deleteJob(UUID id) { jobRepo.deleteById(id); }

    public JobApplication apply(UUID jobId, JobApplicationDto dto) {
        JobListing job = getJobById(jobId);
        if (!job.isActive()) throw new IllegalStateException("This position is no longer active");

        JobApplication app = JobApplication.builder()
                .jobId(jobId)
                .jobTitle(job.getTitle())
                .name(dto.getName())
                .email(dto.getEmail())
                .phone(dto.getPhone())
                .qualification(dto.getQualification())
                .experience(dto.getExperience())
                .coverLetter(dto.getCoverLetter())
                .resumeUrl(dto.getResumeUrl())
                .status(JobApplication.AppStatus.APPLIED)
                .build();
        return appRepo.save(app);
    }

    public Page<JobApplication> getApplications(UUID jobId, int page, int size) {
        return jobId != null
                ? appRepo.findByJobIdOrderByAppliedAtDesc(jobId, PageRequest.of(page, size))
                : appRepo.findAllByOrderByAppliedAtDesc(PageRequest.of(page, size));
    }

    public JobApplication updateAppStatus(UUID id, JobApplication.AppStatus status, String notes) {
        JobApplication app = appRepo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Application not found"));
        app.setStatus(status);
        app.setAdminNotes(notes);
        return appRepo.save(app);
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/careers")
@RequiredArgsConstructor
class CareerController {

    private final CareerService service;

    // Public
    @GetMapping("/jobs")
    public ResponseEntity<ApiResponse<Page<JobListing>>> getActiveJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getActiveJobs(page, size)));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<JobListing>> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getJobById(id)));
    }

    @PostMapping("/jobs/{id}/apply")
    public ResponseEntity<ApiResponse<JobApplication>> apply(
            @PathVariable UUID id, @Valid @RequestBody JobApplicationDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.apply(id, dto), "Application submitted!"));
    }

    // Admin
    @GetMapping("/admin/jobs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<JobListing>>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllJobs(page, size)));
    }

    @PostMapping("/admin/jobs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobListing>> createJob(@Valid @RequestBody JobListingDto dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createJob(dto), "Job posted"));
    }

    @PutMapping("/admin/jobs/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobListing>> updateJob(
            @PathVariable UUID id, @Valid @RequestBody JobListingDto dto) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateJob(id, dto), "Updated"));
    }

    @PatchMapping("/admin/jobs/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobListing>> toggleActive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.toggleActive(id)));
    }

    @DeleteMapping("/admin/jobs/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable UUID id) {
        service.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }

    @GetMapping("/admin/applications")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<JobApplication>>> getApplications(
            @RequestParam(required = false) UUID jobId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getApplications(jobId, page, size)));
    }

    @PatchMapping("/admin/applications/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobApplication>> updateStatus(
            @PathVariable UUID id,
            @RequestParam JobApplication.AppStatus status,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateAppStatus(id, status, notes)));
    }
}
