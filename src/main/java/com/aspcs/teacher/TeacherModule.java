package com.aspcs.teacher;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────
@Entity @Table(name = "teachers")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class Teacher {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "employee_id", nullable = false, unique = true) private String employeeId;
    @Column(name = "full_name",   nullable = false) private String fullName;
    @Column(unique = true) private String email;
    @Column(nullable = false) private String phone;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    private String gender;
    private String qualification;
    private String designation;  // PGT, TGT, PRT
    private String department;
    @Column(name = "joining_date") private LocalDate joiningDate;
    private String address;
    @Column(name = "photo_url") private String photoUrl;
    @Column(unique = true) private String username;
    private String password;
    @Column(name = "is_active") private boolean active = true;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist  protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── DTO ─────────────────────────────────────────────────────
class CreateTeacherRequest {
    @NotBlank public String employeeId;
    @NotBlank public String fullName;
    public String email;
    @NotBlank public String phone;
    public LocalDate dateOfBirth;
    public String gender;
    public String qualification;
    public String designation;
    public String department;
    public LocalDate joiningDate;
    public String address;
    public String photoUrl;
}

// ─── Repository ──────────────────────────────────────────────
interface TeacherRepository extends JpaRepository<Teacher, UUID> {
    Optional<Teacher> findByEmployeeId(String employeeId);
    Optional<Teacher> findByUsername(String username);
    boolean existsByEmployeeId(String employeeId);

    @Query("SELECT t FROM Teacher t WHERE " +
           "LOWER(t.fullName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(t.employeeId) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(t.department) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<Teacher> search(@org.springframework.data.repository.query.Param("q") String q, Pageable p);

    Page<Teacher> findByDepartment(String department, Pageable p);
    long countByActive(boolean active);
}

// ─── Service ─────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class TeacherService {
    private final TeacherRepository repo;
    private final PasswordEncoder   encoder;
    private final com.aspcs.auth.AdminUserRepository adminUserRepository;

    public Page<Teacher> getAll(int page, int size, String search, String dept) {
        Pageable p = PageRequest.of(page, size, Sort.by("fullName"));
        if (search != null && !search.isBlank()) return repo.search(search, p);
        if (dept   != null && !dept.isBlank())   return repo.findByDepartment(dept, p);
        return repo.findAll(p);
    }

    public Teacher getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Teacher not found"));
    }

    public Teacher create(CreateTeacherRequest req) {
        if (repo.existsByEmployeeId(req.employeeId))
            throw new IllegalArgumentException("Employee ID already exists");

        Teacher t = Teacher.builder()
                .employeeId(req.employeeId).fullName(req.fullName)
                .email(req.email).phone(req.phone)
                .dateOfBirth(req.dateOfBirth).gender(req.gender)
                .qualification(req.qualification).designation(req.designation)
                .department(req.department).joiningDate(req.joiningDate)
                .address(req.address).photoUrl(req.photoUrl)
                .username(req.employeeId)
                .password(encoder.encode(req.employeeId + "@aspcs"))
                .active(true).build();
        Teacher saved = repo.save(t);
        provisionLogin(saved, req.email);
        return saved;
    }

    // Creates the admin_users row that lets this teacher log into the ERP.
    // Login email falls back to a synthetic address if the teacher has none,
    // since admin_users.email is unique + not-null. Default password is the
    // same convention as the teacher's own record: employeeId@aspcs.
    private void provisionLogin(Teacher t, String preferredEmail) {
        String loginEmail = (preferredEmail != null && !preferredEmail.isBlank())
                ? preferredEmail
                : t.getEmployeeId().toLowerCase() + "@staff.aspcspatna.ac.in";

        if (adminUserRepository.existsByEmail(loginEmail)) {
            loginEmail = t.getEmployeeId().toLowerCase() + "@staff.aspcspatna.ac.in";
        }

        com.aspcs.auth.entity.AdminUser login = com.aspcs.auth.entity.AdminUser.builder()
                .name(t.getFullName())
                .email(loginEmail)
                .password(encoder.encode(t.getEmployeeId() + "@aspcs"))
                .role(com.aspcs.auth.entity.AdminUser.Role.TEACHER)
                .teacherId(t.getId())
                .build();
        adminUserRepository.save(login);
    }

    public Teacher update(UUID id, CreateTeacherRequest req) {
        Teacher t = getById(id);
        t.setFullName(req.fullName);  t.setEmail(req.email);
        t.setPhone(req.phone);         t.setDepartment(req.department);
        t.setDesignation(req.designation); t.setQualification(req.qualification);
        t.setAddress(req.address);     t.setPhotoUrl(req.photoUrl);
        return repo.save(t);
    }

    public void delete(UUID id) {
        adminUserRepository.findByTeacherId(id).ifPresent(adminUserRepository::delete);
        repo.deleteById(id);
    }

    public java.util.Map<String, Long> getStats() {
        return java.util.Map.of(
            "total",  repo.count(),
            "active", repo.countByActive(true)
        );
    }
}

// ─── Controller ──────────────────────────────────────────────
@RestController @RequestMapping("/teachers") @RequiredArgsConstructor
class TeacherController {
    private final TeacherService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<Teacher>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String department) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, search, department)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getStats()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Teacher>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Teacher>> create(@Valid @RequestBody CreateTeacherRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Teacher added successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Teacher>> update(
            @PathVariable UUID id, @Valid @RequestBody CreateTeacherRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req), "Teacher updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Teacher deleted"));
    }
}
