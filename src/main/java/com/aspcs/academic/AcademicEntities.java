package com.aspcs.academic;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

// ─── Academic Session ────────────────────────────────────────
@Entity @Table(name = "academic_sessions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class AcademicSession {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private String name;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "end_date")   private LocalDate endDate;
    @Column(name = "is_current") private boolean current;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Subject ─────────────────────────────────────────────────
@Entity @Table(name = "subjects")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class Subject {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private String name;
    @Column(nullable = false, unique = true) private String code;
    @Column(name = "max_marks") private int maxMarks = 100;
    @Column(name = "pass_marks") private int passMarks = 33;
    @Column(name = "is_practical") private boolean practical;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── School Class ────────────────────────────────────────────
@Entity @Table(name = "classes")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class SchoolClass {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false) private String name;
    private String section;
    @Column(name = "session_id") private UUID sessionId;
    @Column(name = "class_teacher_id") private UUID classTeacherId;
    private int capacity;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Exam Type ───────────────────────────────────────────────
@Entity @Table(name = "exam_types")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class ExamType {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private String name;
    @Column(name = "short_name") private String shortName;
    private double weightage;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Exam ────────────────────────────────────────────────────
@Entity @Table(name = "exams")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class Exam {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "exam_type_id") private UUID examTypeId;
    @Column(name = "class_id")     private UUID classId;
    @Column(name = "subject_id")   private UUID subjectId;
    @Column(name = "session_id")   private UUID sessionId;
    @Column(name = "exam_date")    private LocalDate examDate;
    @Column(name = "max_marks")    private int maxMarks;
    @Column(name = "pass_marks")   private int passMarks;
    @Column(name = "is_published") private boolean published;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Exam Result ─────────────────────────────────────────────
@Entity @Table(name = "exam_results")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class ExamResult {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "exam_id")    private UUID examId;
    @Column(name = "student_id") private UUID studentId;
    private Double marks;
    private String grade;
    @Column(name = "is_absent")  private boolean absent;
    private String remarks;
    @Column(name = "entered_by") private UUID enteredBy;
    @Column(name = "entered_at", updatable = false) private LocalDateTime enteredAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist  protected void onCreate() { enteredAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate   protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── Attendance ──────────────────────────────────────────────
@Entity @Table(name = "attendance")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class Attendance {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "student_id") private UUID studentId;
    @Column(name = "class_id")   private UUID classId;
    private LocalDate date;
    @Column(nullable = false) private String status = "PRESENT";
    private String remarks;
    @Column(name = "marked_by") private UUID markedBy;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Timetable ───────────────────────────────────────────────
@Entity @Table(name = "timetable")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class Timetable {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "class_id")   private UUID classId;
    @Column(name = "subject_id") private UUID subjectId;
    @Column(name = "teacher_id") private UUID teacherId;
    @Column(name = "day_of_week") private String dayOfWeek;
    @Column(name = "period_no")  private int periodNo;
    @Column(name = "start_time") private java.time.LocalTime startTime;
    @Column(name = "end_time")   private java.time.LocalTime endTime;
    @Column(name = "session_id") private UUID sessionId;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}
