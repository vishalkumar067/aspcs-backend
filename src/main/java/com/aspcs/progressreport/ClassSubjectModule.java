package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Entities ──────────────────────────────────────────────────
@Entity
@Table(name = "class_subject_map")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class ClassSubjectMap {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "class_name", nullable = false) private String className;
    @Column(name = "subject_id", nullable = false) private UUID subjectId;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "teacher_class_assignments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class TeacherClassAssignment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "teacher_id", nullable = false) private UUID teacherId;
    @Column(name = "class_name", nullable = false) private String className;
    private String section;
    @Column(name = "subject_id") private UUID subjectId; // null = class teacher, all subjects
    @Column(name = "is_class_teacher") private boolean classTeacher;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── DTOs ──────────────────────────────────────────────────────
class ClassSubjectRequest {
    @NotBlank public String className;
    @NotNull  public UUID subjectId;
}

class TeacherAssignmentRequest {
    @NotNull  public UUID teacherId;
    @NotBlank public String className;
    public String section;
    public UUID subjectId;       // null => class teacher
    public boolean classTeacher;
}

// ─── Repositories ──────────────────────────────────────────────
interface ClassSubjectMapRepository extends JpaRepository<ClassSubjectMap, UUID> {
    List<ClassSubjectMap> findByClassNameOrderByCreatedAt(String className);
    boolean existsByClassNameAndSubjectId(String className, UUID subjectId);
}

interface TeacherClassAssignmentRepository extends JpaRepository<TeacherClassAssignment, UUID> {
    List<TeacherClassAssignment> findByTeacherId(UUID teacherId);
    List<TeacherClassAssignment> findByClassNameAndSection(String className, String section);
    boolean existsByTeacherIdAndClassNameAndSection(UUID teacherId, String className, String section);
}

// ─── Services ──────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class ClassSubjectMapService {

    private final ClassSubjectMapRepository repo;

    public List<ClassSubjectMap> getForClass(String className) {
        return repo.findByClassNameOrderByCreatedAt(className);
    }

    public ClassSubjectMap add(ClassSubjectRequest req) {
        if (repo.existsByClassNameAndSubjectId(req.className, req.subjectId)) {
            throw new IllegalArgumentException("Subject already mapped to this class");
        }
        return repo.save(ClassSubjectMap.builder()
                .className(req.className)
                .subjectId(req.subjectId)
                .build());
    }

    public void remove(UUID id) {
        repo.deleteById(id);
    }
}

@Service
@RequiredArgsConstructor
class TeacherClassAssignmentService {

    private final TeacherClassAssignmentRepository repo;

    public List<TeacherClassAssignment> getForTeacher(UUID teacherId) {
        return repo.findByTeacherId(teacherId);
    }

    public List<TeacherClassAssignment> getForClass(String className, String section) {
        return repo.findByClassNameAndSection(className, section);
    }

    public TeacherClassAssignment assign(TeacherAssignmentRequest req) {
        return repo.save(TeacherClassAssignment.builder()
                .teacherId(req.teacherId)
                .className(req.className)
                .section(req.section)
                .subjectId(req.classTeacher ? null : req.subjectId)
                .classTeacher(req.classTeacher)
                .build());
    }

    public void remove(UUID id) {
        repo.deleteById(id);
    }

    // Used by AssessmentService to authorize a teacher's access to a class-section.
    public boolean canAccess(UUID teacherId, String className, String section) {
        return repo.existsByTeacherIdAndClassNameAndSection(teacherId, className, section);
    }
}

// ─── Controllers ───────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/class-subjects")
@RequiredArgsConstructor
class ClassSubjectMapController {

    private final ClassSubjectMapService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<List<ClassSubjectMap>>> getForClass(
            @RequestParam String className) {
        return ResponseEntity.ok(ApiResponse.ok(service.getForClass(className)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<ClassSubjectMap>> add(
            @org.springframework.validation.annotation.Validated @RequestBody ClassSubjectRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.add(req), "Subject added to class"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Removed"));
    }
}

@RestController
@RequestMapping("/progress-reports/teacher-assignments")
@RequiredArgsConstructor
class TeacherClassAssignmentController {

    private final TeacherClassAssignmentService service;

    @GetMapping("/by-teacher/{teacherId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<List<TeacherClassAssignment>>> getForTeacher(
            @PathVariable UUID teacherId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getForTeacher(teacherId)));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('TEACHER')")
    public ResponseEntity<ApiResponse<List<TeacherClassAssignment>>> getMine(
            @AuthenticationPrincipal com.aspcs.auth.entity.AdminUser user) {
        return ResponseEntity.ok(ApiResponse.ok(service.getForTeacher(user.getTeacherId())));
    }

    @GetMapping("/by-class")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<List<TeacherClassAssignment>>> getForClass(
            @RequestParam String className, @RequestParam(required = false) String section) {
        return ResponseEntity.ok(ApiResponse.ok(service.getForClass(className, section)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<TeacherClassAssignment>> assign(
            @org.springframework.validation.annotation.Validated @RequestBody TeacherAssignmentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.assign(req), "Teacher assigned"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> remove(@PathVariable UUID id) {
        service.remove(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Removed"));
    }
}
