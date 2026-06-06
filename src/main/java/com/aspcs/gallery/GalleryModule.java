package com.aspcs.gallery;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// ─── Entities ─────────────────────────────────────────────────────────────────
@Entity
@Table(name = "gallery_albums")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
class GalleryAlbum {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private Category category;

    private String coverImageUrl;
    private boolean published;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @OneToMany(mappedBy = "album", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<GalleryImage> images = new ArrayList<>();

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    enum Category { EVENTS, SPORTS, ACADEMICS, INFRASTRUCTURE, CULTURAL, GRADUATION }
}

@Entity
@Table(name = "gallery_images")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
class GalleryImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id", nullable = false)
    private GalleryAlbum album;

    @Column(nullable = false)
    private String url;

    private String thumbnailUrl;
    private String caption;
    private String alt;
    private int    width;
    private int    height;

    @Column(name = "display_order")
    private int displayOrder;
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
class CreateAlbumRequest {
    @NotBlank public String title;
    public String description;
    public GalleryAlbum.Category category;
    public LocalDate eventDate;
}

class AddImagesRequest {
    public List<String> imageUrls;
}

// ─── Repositories ────────────────────────────────────────────────────────────
interface GalleryAlbumRepository extends JpaRepository<GalleryAlbum, UUID> {
    Page<GalleryAlbum> findByPublishedTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<GalleryAlbum> findByCategoryAndPublishedTrueOrderByCreatedAtDesc(
            GalleryAlbum.Category category, Pageable pageable);
}

interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {
    List<GalleryImage> findByAlbumIdOrderByDisplayOrderAsc(UUID albumId);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class GalleryService {

    private final GalleryAlbumRepository albumRepo;
    private final GalleryImageRepository imageRepo;

    public Page<GalleryAlbum> getPublished(int page, int size, String category) {
        Pageable p = PageRequest.of(page, size);
        if (category != null && !category.isBlank()) {
            return albumRepo.findByCategoryAndPublishedTrueOrderByCreatedAtDesc(
                    GalleryAlbum.Category.valueOf(category.toUpperCase()), p);
        }
        return albumRepo.findByPublishedTrueOrderByCreatedAtDesc(p);
    }

    public GalleryAlbum getById(UUID id) {
        return albumRepo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Album not found"));
    }

    public GalleryAlbum create(CreateAlbumRequest req) {
        GalleryAlbum album = GalleryAlbum.builder()
                .title(req.title)
                .description(req.description)
                .category(req.category)
                .eventDate(req.eventDate)
                .published(false)
                .build();
        return albumRepo.save(album);
    }

    public GalleryAlbum addImages(UUID albumId, List<String> imageUrls) {
        GalleryAlbum album  = getById(albumId);
        int order = album.getImages().size();
        for (String url : imageUrls) {
            GalleryImage image = GalleryImage.builder()
                    .album(album)
                    .url(url)
                    .thumbnailUrl(url)
                    .alt(album.getTitle())
                    .displayOrder(order++)
                    .build();
            album.getImages().add(image);
        }
        if (album.getCoverImageUrl() == null && !imageUrls.isEmpty()) {
            album.setCoverImageUrl(imageUrls.get(0));
        }
        return albumRepo.save(album);
    }

    public GalleryAlbum togglePublish(UUID id) {
        GalleryAlbum album = getById(id);
        album.setPublished(!album.isPublished());
        return albumRepo.save(album);
    }

    public void deleteAlbum(UUID id) { albumRepo.deleteById(id); }

    public void deleteImage(UUID albumId, UUID imageId) {
        imageRepo.deleteById(imageId);
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/gallery")
@RequiredArgsConstructor
class GalleryController {

    private final GalleryService service;

    @GetMapping("/albums")
    public ResponseEntity<ApiResponse<Page<GalleryAlbum>>> getPublished(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            @RequestParam(required = false) String category) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublished(page, size, category)));
    }

    @GetMapping("/albums/{id}")
    public ResponseEntity<ApiResponse<GalleryAlbum>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping("/albums")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> create(
            @Valid @RequestBody CreateAlbumRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Album created"));
    }

    @PostMapping("/albums/{id}/images")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> addImages(
            @PathVariable UUID id,
            @RequestBody AddImagesRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.addImages(id, req.imageUrls)));
    }

    @PatchMapping("/albums/{id}/toggle-publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> togglePublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.togglePublish(id)));
    }

    @DeleteMapping("/albums/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAlbum(@PathVariable UUID id) {
        service.deleteAlbum(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Album deleted"));
    }

    @DeleteMapping("/albums/{albumId}/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(
            @PathVariable UUID albumId, @PathVariable UUID imageId) {
        service.deleteImage(albumId, imageId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Image deleted"));
    }
}
