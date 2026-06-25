package com.aspcs.student;

import jakarta.persistence.*;
import lombok.*;

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
