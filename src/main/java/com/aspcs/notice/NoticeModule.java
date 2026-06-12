package com.aspcs.notice;

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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "notices")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private String category = "GENERAL";

    @Column(name = "pdf_url")
    private String pdfUrl;

    @Column(name = "image_url")
    private String imageUrl;

    private boolean important;
    private boolean published;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

// ─── DTO ─────────────────────────────────────────────────────────────────────
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class NoticeRequest {
    @NotBlank(message = "Title is required")
    private String title;
    private String description;
    private String category;
    private String pdfUrl;
    private String imageUrl;
    private boolean important;
    private boolean published;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface NoticeRepository extends JpaRepository<Notice, UUID> {
    Page<Notice> findByPublishedTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<Notice> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<Notice> findTop5ByPublishedTrueOrderByCreatedAtDesc();
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class NoticeService {

    private final NoticeRepository repo;

    public Page<Notice> getAll(int page, int size) {
        return repo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<Notice> getPublished(int page, int size) {
        return repo.findByPublishedTrueOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public List<Notice> getLatest() {
        return repo.findTop5ByPublishedTrueOrderByCreatedAtDesc();
    }

    public Notice getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Notice not found"));
    }

    public Notice create(NoticeRequest req) {
        Notice notice = Notice.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .category(req.getCategory() != null ? req.getCategory() : "GENERAL")
                .pdfUrl(req.getPdfUrl())
                .imageUrl(req.getImageUrl())
                .important(req.isImportant())
                .published(req.isPublished())
                .build();

        if (req.isPublished()) {
            notice.setPublishedAt(LocalDateTime.now());
        }
        return repo.save(notice);
    }

    public Notice update(UUID id, NoticeRequest req) {
        Notice notice = getById(id);
        notice.setTitle(req.getTitle());
        notice.setDescription(req.getDescription());
        notice.setCategory(req.getCategory() != null ? req.getCategory() : notice.getCategory());
        notice.setPdfUrl(req.getPdfUrl());
        notice.setImageUrl(req.getImageUrl());
        notice.setImportant(req.isImportant());

        if (req.isPublished() && !notice.isPublished()) {
            notice.setPublishedAt(LocalDateTime.now());
        }
        notice.setPublished(req.isPublished());
        return repo.save(notice);
    }

    public Notice togglePublish(UUID id) {
        Notice notice = getById(id);
        notice.setPublished(!notice.isPublished());
        if (notice.isPublished() && notice.getPublishedAt() == null) {
            notice.setPublishedAt(LocalDateTime.now());
        }
        return repo.save(notice);
    }

    public void delete(UUID id) {
        repo.deleteById(id);
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/notices")
@RequiredArgsConstructor
class NoticeController {

    private final NoticeService service;

    // Public endpoints
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Page<Notice>>> getPublished(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublished(page, size)));
    }

    @GetMapping("/public/latest")
    public ResponseEntity<ApiResponse<List<Notice>>> getLatest() {
        return ResponseEntity.ok(ApiResponse.ok(service.getLatest()));
    }

    // Admin endpoints
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<Notice>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Notice>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Notice>> create(@Valid @RequestBody NoticeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Notice created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Notice>> update(
            @PathVariable UUID id, @Valid @RequestBody NoticeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req), "Notice updated"));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Notice>> togglePublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.togglePublish(id), "Status updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }
}
