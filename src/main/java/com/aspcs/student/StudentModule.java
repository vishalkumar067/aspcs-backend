package com.aspcs.student;

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
import java.util.UUID;

// ─── Entity — matches V1 students table exactly ──────────────────────────────
@Entity
@Table(name = "students")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class Student {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "admission_no", nullable = false, unique = true) private String admissionNo;
    @Column(name = "roll_no")      private String rollNo;
    @Column(name = "full_name",    nullable = false) private String fullName;
    @Column(name = "date_of_birth") private LocalDate dateOfBirth;
    private String gender;
    private String religion;
    private String category;          // GEN, OBC, SC, ST
    @Column(name = "aadhar_no", unique = true) private String aadharNo;

    @Column(name = "current_class", nullable = false) private String currentClass;
    private String section;
    @Column(name = "admission_date") private LocalDate admissionDate;
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, TRANSFERRED

    @Column(name = "father_name")    private String fatherName;
    @Column(name = "mother_name")    private String motherName;
    @Column(name = "guardian_phone", nullable = false) private String guardianPhone;
    @Column(name = "guardian_email") private String guardianEmail;

    private String address;
    private String city;
    private String state;
    private String pincode;

    @Column(name = "previous_school") private String previousSchool;
    @Column(name = "previous_class")  private String previousClass;
    @Column(name = "photo_url")       private String photoUrl;

    @Column(unique = true)  private String username;
    private String password;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── DTO ─────────────────────────────────────────────────────────────────────
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class StudentRequest {
    @NotBlank private String admissionNo;
    @NotBlank private String fullName;
    @NotBlank private String currentClass;
    @NotBlank private String guardianPhone;
    private String rollNo;
    private LocalDate dateOfBirth;
    private String gender;
    private String religion;
    private String category;
    private String section;
    private LocalDate admissionDate;
    private String fatherName;
    private String motherName;
    private String guardianEmail;
    private String address;
    private String city;
    private String state;
    private String pincode;
    private String previousSchool;
    private String photoUrl;
    private String status;
}

// ─── Repository ──────────────────────────────────────────────────────────────
public interface StudentRepository extends JpaRepository<Student, UUID> {
    Page<Student> findAllByOrderByCreatedAtDesc(Pageable p);
    Page<Student> findByCurrentClassOrderByFullNameAsc(String cls, Pageable p);
    java.util.List<Student> findByCurrentClassAndSectionOrderByFullNameAsc(String cls, String section);
    java.util.List<Student> findByCurrentClassAndSectionAndStatusOrderByFullNameAsc(String cls, String section, String status);

    @Query("SELECT s FROM Student s WHERE " +
           "LOWER(s.fullName) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
           "LOWER(s.admissionNo) LIKE LOWER(CONCAT('%',:q,'%'))")
    Page<Student> search(@org.springframework.data.repository.query.Param("q") String q, Pageable p);

    boolean existsByAdmissionNo(String admissionNo);
    long countByStatus(String status);
    long countByCurrentClass(String cls);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class StudentService {

    private final StudentRepository repo;
    private final PasswordEncoder   encoder;

    public Page<Student> getAll(int page, int size, String search, String cls) {
        Pageable p = PageRequest.of(page, size, Sort.by("fullName"));
        if (search != null && !search.isBlank()) return repo.search(search, p);
        if (cls    != null && !cls.isBlank())    return repo.findByCurrentClassOrderByFullNameAsc(cls, p);
        return repo.findAllByOrderByCreatedAtDesc(p);
    }

    public Student getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Student not found"));
    }

    public Student create(StudentRequest req) {
        if (repo.existsByAdmissionNo(req.getAdmissionNo()))
            throw new IllegalArgumentException("Admission number already exists");

        Student s = new Student();
        s.setAdmissionNo(req.getAdmissionNo());
        s.setFullName(req.getFullName());
        s.setCurrentClass(req.getCurrentClass());
        s.setGuardianPhone(req.getGuardianPhone());
        s.setRollNo(req.getRollNo());
        s.setDateOfBirth(req.getDateOfBirth());
        s.setGender(req.getGender());
        s.setReligion(req.getReligion());
        s.setCategory(req.getCategory());
        s.setSection(req.getSection());
        s.setAdmissionDate(req.getAdmissionDate() != null ? req.getAdmissionDate() : LocalDate.now());
        s.setFatherName(req.getFatherName());
        s.setMotherName(req.getMotherName());
        s.setGuardianEmail(req.getGuardianEmail());
        s.setAddress(req.getAddress());
        s.setCity(req.getCity());
        s.setState(req.getState());
        s.setPincode(req.getPincode());
        s.setPreviousSchool(req.getPreviousSchool());
        s.setPhotoUrl(req.getPhotoUrl());
        s.setStatus("ACTIVE");
        s.setUsername(req.getAdmissionNo().replaceAll("[/\\\\]", "").toLowerCase());
        s.setPassword(encoder.encode(req.getGuardianPhone()));
        return repo.save(s);
    }

    public Student update(UUID id, StudentRequest req) {
        Student s = getById(id);
        s.setFullName(req.getFullName());
        s.setCurrentClass(req.getCurrentClass());
        s.setSection(req.getSection());
        s.setGuardianPhone(req.getGuardianPhone());
        s.setGuardianEmail(req.getGuardianEmail());
        s.setFatherName(req.getFatherName());
        s.setMotherName(req.getMotherName());
        s.setAddress(req.getAddress());
        s.setPhotoUrl(req.getPhotoUrl());
        if (req.getStatus() != null) s.setStatus(req.getStatus());
        return repo.save(s);
    }

    public void delete(UUID id) { repo.deleteById(id); }

    public java.util.Map<String, Long> getStats() {
        return java.util.Map.of(
            "total",    repo.count(),
            "active",   repo.countByStatus("ACTIVE"),
            "inactive", repo.countByStatus("INACTIVE")
        );
    }
}

// ─── Controller ──────────────────────────────────────────────────────────────
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
class StudentController {

    private final StudentService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<Student>>> getAll(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String cls) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, search, cls)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getStats()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Student>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Student>> create(@Valid @RequestBody StudentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Student enrolled"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Student>> update(
            @PathVariable UUID id, @Valid @RequestBody StudentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req), "Student updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Student removed"));
    }
}
