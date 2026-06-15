package com.aspcs.gallery;

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
import java.util.List;
import java.util.UUID;

// ─── GalleryAlbum Entity ──────────────────────────────────────────────────────
@Entity
@Table(name = "gallery_albums")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class GalleryAlbum {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private String title;
    @Column(columnDefinition = "TEXT") private String description;
    private String category = "EVENTS";
    @Column(name = "cover_image_url") private String coverImageUrl;
    private boolean published = false;
    @Column(name = "event_date") private LocalDate eventDate;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── GalleryImage Entity ──────────────────────────────────────────────────────
@Entity
@Table(name = "gallery_images")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class GalleryImage {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "album_id") private UUID albumId;
    @Column(nullable = false) private String url;
    @Column(name = "thumbnail_url") private String thumbnailUrl;
    @Column(name = "public_id") private String publicId;
    private String caption;
    private String alt;
    @Column(name = "display_order") private int displayOrder = 0;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── DTOs ─────────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class AlbumRequest {
    @NotBlank(message = "Title is required")
    private String title;
    private String    description;
    private String    category;
    private String    coverImageUrl;
    private boolean   published;
    private LocalDate eventDate;
}

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class ImageUrlRequest {
    @NotBlank(message = "URL is required")
    private String url;        // Cloudinary secure_url
    private String caption;
    private String alt;
    private String publicId;   // Cloudinary public_id (optional)
}

// ─── Repositories ─────────────────────────────────────────────────────────────
interface GalleryAlbumRepository extends JpaRepository<GalleryAlbum, UUID> {
    Page<GalleryAlbum> findByPublishedTrueOrderByCreatedAtDesc(Pageable p);
    Page<GalleryAlbum> findAllByOrderByCreatedAtDesc(Pageable p);
}

interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {
    List<GalleryImage> findByAlbumIdOrderByDisplayOrderAsc(UUID albumId);
    long countByAlbumId(UUID albumId);
}

// ─── Service ──────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class GalleryService {

    private final GalleryAlbumRepository albumRepo;
    private final GalleryImageRepository imageRepo;

    public Page<GalleryAlbum> getAllAdmin(int page, int size) {
        return albumRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<GalleryAlbum> getPublished(int page, int size) {
        return albumRepo.findByPublishedTrueOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public GalleryAlbum getById(UUID id) {
        return albumRepo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Album not found"));
    }

    public GalleryAlbum createAlbum(AlbumRequest req) {
        GalleryAlbum a = new GalleryAlbum();
        a.setTitle(req.getTitle());
        a.setDescription(req.getDescription());
        a.setCategory(req.getCategory() != null ? req.getCategory() : "EVENTS");
        a.setCoverImageUrl(req.getCoverImageUrl());
        a.setPublished(req.isPublished());
        a.setEventDate(req.getEventDate());
        return albumRepo.save(a);
    }

    public GalleryAlbum updateAlbum(UUID id, AlbumRequest req) {
        GalleryAlbum a = getById(id);
        a.setTitle(req.getTitle());
        a.setDescription(req.getDescription());
        if (req.getCategory()      != null) a.setCategory(req.getCategory());
        if (req.getCoverImageUrl() != null) a.setCoverImageUrl(req.getCoverImageUrl());
        a.setPublished(req.isPublished());
        a.setEventDate(req.getEventDate());
        return albumRepo.save(a);
    }

    public GalleryAlbum togglePublish(UUID id) {
        GalleryAlbum a = getById(id);
        a.setPublished(!a.isPublished());
        return albumRepo.save(a);
    }

    public GalleryImage addImageByUrl(UUID albumId, ImageUrlRequest req) {
        GalleryAlbum album = getById(albumId);
        long order = imageRepo.countByAlbumId(albumId);

        GalleryImage img = new GalleryImage();
        img.setAlbumId(albumId);
        img.setUrl(req.getUrl());
        img.setCaption(req.getCaption());
        img.setAlt(req.getAlt());
        img.setPublicId(req.getPublicId());
        img.setDisplayOrder((int) order);
        GalleryImage saved = imageRepo.save(img);

        // Auto-set cover image if first image
        if (album.getCoverImageUrl() == null) {
            album.setCoverImageUrl(req.getUrl());
            albumRepo.save(album);
        }
        return saved;
    }

    public List<GalleryImage> getImages(UUID albumId) {
        return imageRepo.findByAlbumIdOrderByDisplayOrderAsc(albumId);
    }

    public void deleteImage(UUID imageId) { imageRepo.deleteById(imageId); }
    public void deleteAlbum(UUID id)      { albumRepo.deleteById(id); }
}

// ─── Controller ───────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/gallery")
@RequiredArgsConstructor
class GalleryController {

    private final GalleryService service;

    // Public
    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Page<GalleryAlbum>>> getPublished(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublished(page, size)));
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<ApiResponse<GalleryAlbum>> getPublicById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    // Admin
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<GalleryAlbum>>> getAll(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllAdmin(page, size)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<GalleryAlbum>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @GetMapping("/{id}/images")
    public ResponseEntity<ApiResponse<List<GalleryImage>>> getImages(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getImages(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> createAlbum(
            @Valid @RequestBody AlbumRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createAlbum(req), "Album created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> updateAlbum(
            @PathVariable UUID id, @Valid @RequestBody AlbumRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateAlbum(id, req), "Album updated"));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> togglePublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.togglePublish(id)));
    }

    // Frontend uploads to Cloudinary → sends URL here
    @PostMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryImage>> addImage(
            @PathVariable UUID id, @Valid @RequestBody ImageUrlRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.addImageByUrl(id, req), "Image added"));
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable UUID imageId) {
        service.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Image deleted"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteAlbum(@PathVariable UUID id) {
        service.deleteAlbum(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Album deleted"));
    }
}
