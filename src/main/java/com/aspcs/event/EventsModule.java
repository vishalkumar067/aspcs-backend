package com.aspcs.event;

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
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private String venue;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "is_highlight")
    private boolean highlight = false;

    private String category = "ACADEMIC";

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}

// ─── DTO ─────────────────────────────────────────────────────────────────────
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
class EventRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    private LocalDate endDate;
    private String venue;
    private String imageUrl;
    private boolean highlight;
    private String category;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface EventRepository extends JpaRepository<Event, UUID> {
    Page<Event> findAllByOrderByStartDateDesc(Pageable pageable);
    List<Event> findByHighlightTrueOrderByStartDateDesc();
    List<Event> findTop5ByStartDateGreaterThanEqualOrderByStartDateAsc(LocalDate date);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class EventService {

    private final EventRepository repo;

    public Page<Event> getAll(int page, int size) {
        return repo.findAllByOrderByStartDateDesc(PageRequest.of(page, size));
    }

    public List<Event> getHighlights() {
        return repo.findByHighlightTrueOrderByStartDateDesc();
    }

    public List<Event> getUpcoming() {
        return repo.findTop5ByStartDateGreaterThanEqualOrderByStartDateAsc(LocalDate.now());
    }

    public Event getById(UUID id) {
        return repo.findById(id).orElseThrow(
                () -> new jakarta.persistence.EntityNotFoundException("Event not found"));
    }

    public Event create(EventRequest req) {
        Event e = new Event();
        e.setTitle(req.getTitle());
        e.setDescription(req.getDescription());
        e.setStartDate(req.getStartDate());
        e.setEndDate(req.getEndDate());
        e.setVenue(req.getVenue());
        e.setImageUrl(req.getImageUrl());
        e.setHighlight(req.isHighlight());
        e.setCategory(req.getCategory() != null ? req.getCategory() : "ACADEMIC");
        return repo.save(e);
    }

    public Event update(UUID id, EventRequest req) {
        Event e = getById(id);
        e.setTitle(req.getTitle());
        e.setDescription(req.getDescription());
        e.setStartDate(req.getStartDate());
        e.setEndDate(req.getEndDate());
        e.setVenue(req.getVenue());
        e.setImageUrl(req.getImageUrl());
        e.setHighlight(req.isHighlight());
        if (req.getCategory() != null) e.setCategory(req.getCategory());
        return repo.save(e);
    }

    public void delete(UUID id) {
        repo.deleteById(id);
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
class EventController {

    private final EventService service;

    @GetMapping("/public")
    public ResponseEntity<ApiResponse<Page<Event>>> getPublic(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size)));
    }

    @GetMapping("/public/highlights")
    public ResponseEntity<ApiResponse<List<Event>>> getHighlights() {
        return ResponseEntity.ok(ApiResponse.ok(service.getHighlights()));
    }

    @GetMapping("/public/upcoming")
    public ResponseEntity<ApiResponse<List<Event>>> getUpcoming() {
        return ResponseEntity.ok(ApiResponse.ok(service.getUpcoming()));
    }

    @GetMapping("/public/{id}")
    public ResponseEntity<ApiResponse<Event>> getPublicById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<Event>>> getAll(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "100") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Event>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Event>> create(@Valid @RequestBody EventRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Event created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Event>> update(
            @PathVariable UUID id, @Valid @RequestBody EventRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req), "Event updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Deleted"));
    }
}
