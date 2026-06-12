package com.aspcs.gallery;

import com.aspcs.common.ApiResponse;
import com.aspcs.upload.CloudinaryService;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// ─── GalleryAlbum Entity ─────────────────────────────────────────────────────
@Entity
@Table(name = "gallery_albums")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class GalleryAlbum {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    private String category = "EVENTS";

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    private boolean published;

    @Column(name = "event_date")
    private LocalDate eventDate;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "albumId", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<GalleryImage> images = new ArrayList<>();

    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── GalleryImage Entity ─────────────────────────────────────────────────────
@Entity
@Table(name = "gallery_images")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
class GalleryImage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "album_id")
    private UUID albumId;

    @Column(nullable = false)
    private String url;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "public_id")
    private String publicId;

    private String caption;
    private String alt;
    private Integer width;
    private Integer height;

    @Column(name = "display_order")
    @Builder.Default
    private int displayOrder = 0;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Repositories ─────────────────────────────────────────────────────────────
interface GalleryAlbumRepository extends JpaRepository<GalleryAlbum, UUID> {
    Page<GalleryAlbum> findByPublishedTrueOrderByCreatedAtDesc(Pageable pageable);
    Page<GalleryAlbum> findAllByOrderByCreatedAtDesc(Pageable pageable);
}

interface GalleryImageRepository extends JpaRepository<GalleryImage, UUID> {
    List<GalleryImage> findByAlbumIdOrderByDisplayOrderAsc(UUID albumId);
    void deleteByAlbumId(UUID albumId);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class GalleryService {

    private final GalleryAlbumRepository albumRepo;
    private final GalleryImageRepository imageRepo;
    private final CloudinaryService       cloudinary;

    public Page<GalleryAlbum> getAll(int page, int size) {
        return albumRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public Page<GalleryAlbum> getPublished(int page, int size) {
        return albumRepo.findByPublishedTrueOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public GalleryAlbum getById(UUID id) {
        return albumRepo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Album not found"));
    }

    public GalleryAlbum createAlbum(String title, String description, String category, boolean published) {
        GalleryAlbum album = GalleryAlbum.builder()
                .title(title)
                .description(description)
                .category(category != null ? category : "EVENTS")
                .published(published)
                .build();
        return albumRepo.save(album);
    }

    public GalleryAlbum updateAlbum(UUID id, String title, String description,
                                    String category, boolean published) {
        GalleryAlbum album = getById(id);
        album.setTitle(title);
        album.setDescription(description);
        album.setCategory(category != null ? category : album.getCategory());
        album.setPublished(published);
        return albumRepo.save(album);
    }

    public GalleryImage addImage(UUID albumId, MultipartFile file, String caption) throws Exception {
        GalleryAlbum album = getById(albumId);
        String url = cloudinary.uploadImage(file, "gallery");

        GalleryImage image = GalleryImage.builder()
                .albumId(albumId)
                .url(url)
                .caption(caption)
                .build();

        GalleryImage saved = imageRepo.save(image);

        // set cover if first image
        if (album.getCoverImageUrl() == null) {
            album.setCoverImageUrl(url);
            albumRepo.save(album);
        }

        return saved;
    }

    public List<GalleryImage> getImages(UUID albumId) {
        return imageRepo.findByAlbumIdOrderByDisplayOrderAsc(albumId);
    }

    public void deleteImage(UUID imageId) {
        imageRepo.deleteById(imageId);
    }

    public void deleteAlbum(UUID id) {
        albumRepo.deleteById(id);
    }

    public GalleryAlbum togglePublish(UUID id) {
        GalleryAlbum album = getById(id);
        album.setPublished(!album.isPublished());
        return albumRepo.save(album);
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/gallery")
@RequiredArgsConstructor
class GalleryController {

    private final GalleryService service;

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Page<GalleryAlbum>>> getPublished(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPublished(page, size)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<GalleryAlbum>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size)));
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
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean published) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.createAlbum(title, description, category, published)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> updateAlbum(
            @PathVariable UUID id,
            @RequestParam String title,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean published) {
        return ResponseEntity.ok(ApiResponse.ok(
                service.updateAlbum(id, title, description, category, published)));
    }

    @PostMapping("/{id}/images")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryImage>> addImage(
            @PathVariable UUID id,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String caption) throws Exception {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.addImage(id, file, caption)));
    }

    @DeleteMapping("/images/{imageId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteImage(@PathVariable UUID imageId) {
        service.deleteImage(imageId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Image deleted"));
    }

    @PatchMapping("/{id}/publish")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<GalleryAlbum>> togglePublish(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.togglePublish(id)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.deleteAlbum(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }
}
