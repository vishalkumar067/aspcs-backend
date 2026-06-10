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

// ─── Job Entity ──────────────────────────────────────────────────────────────
@Entity
@Table(name = "job_listings")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
class JobListing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String department;

    @Enumerated(EnumType.STRING)
    private JobType type;

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

    private boolean active;
    private int vacancies;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    enum JobType { FULL_TIME, PART_TIME, CONTRACT, TEMPORARY }
}

// ─── Application Entity ───────────────────────────────────────────────────────
@Entity
@Table(name = "job_applications")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_id", nullable = false)
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
    private AppStatus status;

    @Column(name = "admin_notes", columnDefinition = "TEXT")
    private String adminNotes;

    @Column(name = "applied_at", updatable = false)
    private LocalDateTime appliedAt;

    @PrePersist
    protected void onCreate() {
        appliedAt = LocalDateTime.now();
        if (status == null) status = AppStatus.APPLIED;
    }

    enum AppStatus { APPLIED, SHORTLISTED, INTERVIEW, SELECTED, REJECTED }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
class CreateJobRequest {
    @NotBlank public String title;
    @NotBlank public String department;
    public JobListing.JobType type;
    @NotBlank public String description;
    public String requirements;
    public String responsibilities;
    public String experience;
    public String qualification;
    public String salary;
    public LocalDate lastDate;
    public int vacancies;
}

class ApplyJobRequest {
    @NotBlank public String name;
    @NotBlank @Email public String email;
    public String phone;
    public String qualification;
    public String experience;
    public String coverLetter;
    public String resumeUrl;
}

// ─── Repositories ─────────────────────────────────────────────────────────────
interface JobListingRepository extends JpaRepository<JobListing, UUID> {
    Page<JobListing> findByActiveTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<JobListing> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {
    Page<JobApplication> findByJobIdOrderByAppliedAtDesc(UUID jobId, Pageable pageable);
    Page<JobApplication> findAllByOrderByAppliedAtDesc(Pageable pageable);
    long countByJobId(UUID jobId);
    long countByStatus(JobApplication.AppStatus status);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class CareerService {

    private final JobListingRepository    jobRepo;
    private final JobApplicationRepository appRepo;

    // Jobs
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

    public JobListing createJob(CreateJobRequest req) {
        return jobRepo.save(JobListing.builder()
                .title(req.title).department(req.department).type(req.type)
                .description(req.description).requirements(req.requirements)
                .responsibilities(req.responsibilities).experience(req.experience)
                .qualification(req.qualification).salary(req.salary)
                .lastDate(req.lastDate).vacancies(req.vacancies).active(true)
                .build());
    }

    public JobListing toggleActive(UUID id) {
        JobListing job = getJobById(id);
        job.setActive(!job.isActive());
        return jobRepo.save(job);
    }

    public void deleteJob(UUID id) { jobRepo.deleteById(id); }

    // Applications
    public JobApplication apply(UUID jobId, ApplyJobRequest req) {
        JobListing job = getJobById(jobId);
        if (!job.isActive()) throw new IllegalArgumentException("This position is no longer accepting applications");

        return appRepo.save(JobApplication.builder()
                .jobId(jobId).jobTitle(job.getTitle())
                .name(req.name).email(req.email).phone(req.phone)
                .qualification(req.qualification).experience(req.experience)
                .coverLetter(req.coverLetter).resumeUrl(req.resumeUrl)
                .build());
    }

    public Page<JobApplication> getApplications(UUID jobId, int page, int size) {
        return appRepo.findByJobIdOrderByAppliedAtDesc(jobId, PageRequest.of(page, size));
    }

    public Page<JobApplication> getAllApplications(int page, int size) {
        return appRepo.findAllByOrderByAppliedAtDesc(PageRequest.of(page, size));
    }

    public JobApplication updateApplicationStatus(UUID id, JobApplication.AppStatus status, String notes) {
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
            @PathVariable UUID id,
            @Valid @RequestBody ApplyJobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.apply(id, req),
                        "Application submitted successfully! We will contact you soon."));
    }

    // Admin
    @GetMapping("/admin/jobs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<JobListing>>> getAllJobs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllJobs(page, size)));
    }

    @PostMapping("/admin/jobs")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobListing>> createJob(@Valid @RequestBody CreateJobRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createJob(req), "Job posted successfully"));
    }

    @PatchMapping("/admin/jobs/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobListing>> toggleJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.toggleActive(id)));
    }

    @DeleteMapping("/admin/jobs/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteJob(@PathVariable UUID id) {
        service.deleteJob(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Job deleted"));
    }

    @GetMapping("/admin/applications")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<JobApplication>>> getAllApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllApplications(page, size)));
    }

    @GetMapping("/admin/jobs/{id}/applications")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<JobApplication>>> getApplications(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getApplications(id, page, size)));
    }

    @PatchMapping("/admin/applications/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<JobApplication>> updateStatus(
            @PathVariable UUID id,
            @RequestParam JobApplication.AppStatus status,
            @RequestParam(required = false) String notes) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateApplicationStatus(id, status, notes)));
    }
}
