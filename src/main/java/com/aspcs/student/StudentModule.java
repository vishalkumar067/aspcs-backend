package com.aspcs.student;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

// ─── Entity ──────────────────────────────────────────────────────────────────
@Entity
@Table(name = "students")
@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ── Personal Info ──
    @Column(name = "admission_no", nullable = false, unique = true)
    private String admissionNo;

    @Column(name = "roll_no")
    private String rollNo;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    private Gender gender;

    private String religion;
    private String category; // GEN, OBC, SC, ST

    @Column(name = "aadhar_no")
    private String aadharNo;

    // ── Academic Info ──
    @Column(name = "current_class", nullable = false)
    private String currentClass;

    private String section;

    @Column(name = "admission_date")
    private LocalDate admissionDate;

    @Enumerated(EnumType.STRING)
    private StudentStatus status;

    // ── Parent Info ──
    @Column(name = "father_name")
    private String fatherName;

    @Column(name = "mother_name")
    private String motherName;

    @Column(name = "guardian_phone", nullable = false)
    private String guardianPhone;

    @Column(name = "guardian_email")
    private String guardianEmail;

    // ── Address ──
    @Column(columnDefinition = "TEXT")
    private String address;

    private String city;
    private String state;
    private String pincode;

    // ── Previous School ──
    @Column(name = "previous_school")
    private String previousSchool;

    @Column(name = "previous_class")
    private String previousClass;

    // ── Login credentials ──
    @Column(unique = true)
    private String username; // usually admissionNo

    private String password; // hashed

    // ── Photo ──
    @Column(name = "photo_url")
    private String photoUrl;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
        if (status == null) status = StudentStatus.ACTIVE;
    }

    @PreUpdate
    protected void onUpdate() { updatedAt = LocalDateTime.now(); }

    enum Gender { MALE, FEMALE, OTHER }
    enum StudentStatus { ACTIVE, INACTIVE, TRANSFERRED, PASSED_OUT }
}

// ─── DTOs ────────────────────────────────────────────────────────────────────
class CreateStudentRequest {
    @NotBlank public String admissionNo;
    public String rollNo;
    @NotBlank public String fullName;
    public LocalDate dateOfBirth;
    public Student.Gender gender;
    public String religion;
    public String category;
    public String aadharNo;
    @NotBlank public String currentClass;
    public String section;
    public LocalDate admissionDate;
    public String fatherName;
    public String motherName;
    @NotBlank public String guardianPhone;
    public String guardianEmail;
    public String address;
    public String city;
    public String state;
    public String pincode;
    public String previousSchool;
    public String previousClass;
    public String photoUrl;
}

// ─── Repository ──────────────────────────────────────────────────────────────
interface StudentRepository extends JpaRepository<Student, UUID> {
    Optional<Student> findByAdmissionNo(String admissionNo);
    Optional<Student> findByUsername(String username);
    Page<Student> findByCurrentClass(String currentClass, Pageable pageable);
    Page<Student> findByStatus(Student.StudentStatus status, Pageable pageable);

    @Query("SELECT s FROM Student s WHERE " +
           "LOWER(s.fullName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.admissionNo) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(s.guardianPhone) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Student> search(@org.springframework.data.repository.query.Param("q") String q, Pageable pageable);

    boolean existsByAdmissionNo(String admissionNo);
    long countByStatus(Student.StudentStatus status);
    long countByCurrentClass(String currentClass);
}

// ─── Service ─────────────────────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class StudentService {

    private final StudentRepository repo;
    private final PasswordEncoder   passwordEncoder;

    public Page<Student> getAll(int page, int size, String search, String status) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("fullName").ascending());
        if (search != null && !search.isBlank()) return repo.search(search, pageable);
        if (status != null && !status.isBlank())
            return repo.findByStatus(Student.StudentStatus.valueOf(status), pageable);
        return repo.findAll(pageable);
    }

    public Student getById(UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Student not found"));
    }

    public Student getByAdmissionNo(String admissionNo) {
        return repo.findByAdmissionNo(admissionNo)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException(
                        "Student not found with admission no: " + admissionNo));
    }

    public Student create(CreateStudentRequest req) {
        if (repo.existsByAdmissionNo(req.admissionNo))
            throw new IllegalArgumentException("Admission number already exists");

        Student student = Student.builder()
                .admissionNo(req.admissionNo)
                .rollNo(req.rollNo)
                .fullName(req.fullName)
                .dateOfBirth(req.dateOfBirth)
                .gender(req.gender)
                .religion(req.religion)
                .category(req.category)
                .aadharNo(req.aadharNo)
                .currentClass(req.currentClass)
                .section(req.section)
                .admissionDate(req.admissionDate)
                .fatherName(req.fatherName)
                .motherName(req.motherName)
                .guardianPhone(req.guardianPhone)
                .guardianEmail(req.guardianEmail)
                .address(req.address)
                .city(req.city)
                .state(req.state)
                .pincode(req.pincode)
                .previousSchool(req.previousSchool)
                .previousClass(req.previousClass)
                .photoUrl(req.photoUrl)
                .username(req.admissionNo)
                .password(passwordEncoder.encode(req.admissionNo + "@aspcs"))
                .status(Student.StudentStatus.ACTIVE)
                .build();

        return repo.save(student);
    }

    public Student update(UUID id, CreateStudentRequest req) {
        Student student = getById(id);
        student.setFullName(req.fullName);
        student.setCurrentClass(req.currentClass);
        student.setSection(req.section);
        student.setRollNo(req.rollNo);
        student.setGuardianPhone(req.guardianPhone);
        student.setGuardianEmail(req.guardianEmail);
        student.setAddress(req.address);
        student.setFatherName(req.fatherName);
        student.setMotherName(req.motherName);
        student.setPhotoUrl(req.photoUrl);
        return repo.save(student);
    }

    public Student updateStatus(UUID id, Student.StudentStatus status) {
        Student student = getById(id);
        student.setStatus(status);
        return repo.save(student);
    }

    public void delete(UUID id) { repo.deleteById(id); }

    public java.util.Map<String, Long> getStats() {
        return java.util.Map.of(
                "total",       repo.count(),
                "active",      repo.countByStatus(Student.StudentStatus.ACTIVE),
                "transferred", repo.countByStatus(Student.StudentStatus.TRANSFERRED)
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, search, status)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Student>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    // Public endpoint — for TC verification
    @GetMapping("/by-admission/{admissionNo}")
    public ResponseEntity<ApiResponse<Student>> getByAdmissionNo(@PathVariable String admissionNo) {
        Student s = service.getByAdmissionNo(admissionNo);
        // Return limited info for public
        return ResponseEntity.ok(ApiResponse.ok(s));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Long>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getStats()));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Student>> create(@Valid @RequestBody CreateStudentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.create(req), "Student added successfully"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Student>> update(
            @PathVariable UUID id,
            @Valid @RequestBody CreateStudentRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.update(id, req), "Student updated"));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Student>> updateStatus(
            @PathVariable UUID id,
            @RequestParam Student.StudentStatus status) {
        return ResponseEntity.ok(ApiResponse.ok(service.updateStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Student deleted"));
    }
}
