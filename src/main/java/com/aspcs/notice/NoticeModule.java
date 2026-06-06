package com.aspcs.notice;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "notices")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Category category;

    private String pdfUrl;
    private String imageUrl;

    @Column(nullable = false)
    private boolean important;

    @Column(nullable = false)
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
        if (published && publishedAt == null) publishedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    enum Category { ACADEMIC, EXAM, HOLIDAY, ADMISSION, GENERAL, URGENT }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
class CreateNoticeRequest {
    @NotBlank(message = "Title is required")
    public String title;
    public String description;
    @NotNull(message = "Category is required")
    public Notice.Category category;
    public String  pdfUrl;
    public boolean important;
    public LocalDateTime expiresAt;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface NoticeRepository extends JpaRepository<Notice, UUID> {
    Page<Notice> findByPublishedTrueOrderByPublishedAtDesc(Pageable pageable);
    Page<Notice> findByCategoryAndPublishedTrueOrderByPublishedAtDesc(
            Notice.Category category, Pageable pageable);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class NoticeService {

    private final NoticeRepository repo;

    public Page<Notice> getPublished(int page, int size, String category) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending());
        if (category != null && !category.isBlank()) {
            return repo.findByCategoryAndPublishedTrueOrderByPublishedAtDesc(
                    Notice.Category.valueOf(category.toUpperCase()), pageable);
        }
        return repo.findByPublishedTrueOrderByPublishedAtDesc(pageable);
    }

    public Page<Notice> getAll(int page, int size) {
        return repo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public Notice getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Notice not found"));
    }

    public Notice create(CreateNoticeRequest req) {
        Notice notice = Notice.builder()
                .title(req.title)
                .description(req.description)
                .category(req.category)
                .pdfUrl(req.pdfUrl)
                .important(req.important)
                .published(false)
                .expiresAt(req.expiresAt)
                .build();
        return repo.save(notice);
    }

    public Notice update(UUID id, CreateNoticeRequest req) {
        Notice notice = getById(id);
        notice.setTitle(req.title);
        notice.setDescription(req.description);
        notice.setCategory(req.category);
        notice.setPdfUrl(req.pdfUrl);
        notice.setImportant(req.important);
        notice.setExpiresAt(req.expiresAt);
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

    // Public
    @GetMapping
    public ResponseEntity<ApiResponse<Page<Notice>>> getPublished(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublished(page, size, category)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Notice>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    // Admin only
    @GetMapping("/admin/all")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<Notice>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Notice>> create(@Valid @RequestBody CreateNoticeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Notice created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Notice>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateNoticeRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req), "Notice updated"));
    }

    @PatchMapping("/{id}/toggle-publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Notice>> togglePublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.togglePublish(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Notice deleted"));
    }
}
