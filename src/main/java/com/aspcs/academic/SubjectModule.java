package com.aspcs.academic;

import com.aspcs.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

// ─── Repository ────────────────────────────────────────────────
// subjects already existed (seeded in V2) but had no API at all until now.
public interface SubjectRepository extends JpaRepository<Subject, UUID> {
    List<Subject> findAllByOrderByNameAsc();
    boolean existsByCode(String code);
}

// ─── DTO ───────────────────────────────────────────────────────
class SubjectRequest {
    @NotBlank public String name;
    @NotBlank public String code;
    public int maxMarks = 100;
    public int passMarks = 33;
    public boolean practical;
}

// ─── Service ───────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class SubjectService {

    private final SubjectRepository repo;

    public List<Subject> getAll() {
        return repo.findAllByOrderByNameAsc();
    }

    public Subject create(SubjectRequest req) {
        if (repo.existsByCode(req.code.toUpperCase())) {
            throw new IllegalArgumentException("Subject code already exists");
        }
        Subject s = Subject.builder()
                .name(req.name)
                .code(req.code.toUpperCase())
                .maxMarks(req.maxMarks)
                .passMarks(req.passMarks)
                .practical(req.practical)
                .build();
        return repo.save(s);
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/subjects")
@RequiredArgsConstructor
class SubjectController {

    private final SubjectService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<List<Subject>>> getAll() {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Subject>> create(@Valid @RequestBody SubjectRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Subject created"));
    }
}
