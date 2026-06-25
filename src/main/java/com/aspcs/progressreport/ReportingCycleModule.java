package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Entity ────────────────────────────────────────────────────
@Entity
@Table(name = "reporting_cycles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class ReportingCycle {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(nullable = false) private String name;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false)   private LocalDate endDate;
    @Column(name = "session_id") private UUID sessionId;

    @Column(nullable = false) private String status = "OPEN"; // OPEN, CLOSED, ARCHIVED

    @Column(name = "created_by") private UUID createdBy;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── DTO ───────────────────────────────────────────────────────
class CycleRequest {
    @NotBlank public String name;
    @NotNull  public LocalDate startDate;
    @NotNull  public LocalDate endDate;
    public UUID sessionId;
}

// ─── Repository ────────────────────────────────────────────────
interface ReportingCycleRepository extends JpaRepository<ReportingCycle, UUID> {
    List<ReportingCycle> findAllByOrderByStartDateDesc();
    List<ReportingCycle> findByStatusOrderByStartDateDesc(String status);
}

// ─── Service ───────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class ReportingCycleService {

    private final ReportingCycleRepository repo;

    public List<ReportingCycle> getAll(String status) {
        if (status != null && !status.isBlank()) {
            return repo.findByStatusOrderByStartDateDesc(status.toUpperCase());
        }
        return repo.findAllByOrderByStartDateDesc();
    }

    public ReportingCycle getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Reporting cycle not found"));
    }

    public ReportingCycle create(CycleRequest req, UUID createdBy) {
        if (req.endDate.isBefore(req.startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        ReportingCycle c = ReportingCycle.builder()
                .name(req.name)
                .startDate(req.startDate)
                .endDate(req.endDate)
                .sessionId(req.sessionId)
                .status("OPEN")
                .createdBy(createdBy)
                .build();
        return repo.save(c);
    }

    public ReportingCycle update(UUID id, CycleRequest req) {
        ReportingCycle c = getById(id);
        if ("ARCHIVED".equals(c.getStatus())) {
            throw new IllegalArgumentException("Cannot edit an archived cycle");
        }
        if (req.endDate.isBefore(req.startDate)) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        c.setName(req.name);
        c.setStartDate(req.startDate);
        c.setEndDate(req.endDate);
        c.setSessionId(req.sessionId);
        return repo.save(c);
    }

    public ReportingCycle close(UUID id) {
        ReportingCycle c = getById(id);
        if (!"OPEN".equals(c.getStatus())) {
            throw new IllegalArgumentException("Only an OPEN cycle can be closed");
        }
        c.setStatus("CLOSED");
        return repo.save(c);
    }

    public ReportingCycle reopen(UUID id) {
        ReportingCycle c = getById(id);
        if (!"CLOSED".equals(c.getStatus())) {
            throw new IllegalArgumentException("Only a CLOSED cycle can be reopened");
        }
        c.setStatus("OPEN");
        return repo.save(c);
    }

    public ReportingCycle archive(UUID id) {
        ReportingCycle c = getById(id);
        if ("OPEN".equals(c.getStatus())) {
            throw new IllegalArgumentException("Close the cycle before archiving it");
        }
        c.setStatus("ARCHIVED");
        return repo.save(c);
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/cycles")
@RequiredArgsConstructor
class ReportingCycleController {

    private final ReportingCycleService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<List<ReportingCycle>>> getAll(
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<ReportingCycle>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReportingCycle>> create(
            @Valid @RequestBody CycleRequest req,
            @AuthenticationPrincipal com.aspcs.auth.entity.AdminUser user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req, user.getId()), "Reporting cycle created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReportingCycle>> update(
            @PathVariable UUID id, @Valid @RequestBody CycleRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req), "Reporting cycle updated"));
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReportingCycle>> close(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.close(id), "Cycle closed"));
    }

    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReportingCycle>> reopen(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.reopen(id), "Cycle reopened"));
    }

    @PostMapping("/{id}/archive")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ReportingCycle>> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.archive(id), "Cycle archived"));
    }
}
