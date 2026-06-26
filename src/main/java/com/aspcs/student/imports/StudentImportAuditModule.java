package com.aspcs.student.imports;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Entities ──────────────────────────────────────────────────
@Entity
@Table(name = "student_import_batches")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class StudentImportBatch {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "total_rows")    private int totalRows;
    @Column(name = "created_count") private int createdCount;
    @Column(name = "updated_count") private int updatedCount;
    @Column(name = "failed_count")  private int failedCount;
    @Column(name = "imported_by")   private UUID importedBy;
    @Column(name = "imported_at")   private LocalDateTime importedAt;

    @PrePersist protected void onCreate() { importedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "student_import_rows")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class StudentImportRow {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "batch_id", nullable = false) private UUID batchId;
    @Column(name = "row_number", nullable = false) private int rowNumber;
    @Column(name = "admission_no") private String admissionNo;
    @Column(name = "student_id")   private UUID studentId;
    @Column(nullable = false)      private String outcome; // CREATED, UPDATED, FAILED
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;

    @Column(name = "before_data", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String beforeData;

    @Column(name = "after_data", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String afterData;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Repositories ──────────────────────────────────────────────
interface StudentImportBatchRepository extends JpaRepository<StudentImportBatch, UUID> {
    List<StudentImportBatch> findAllByOrderByImportedAtDesc();
}

interface StudentImportRowRepository extends JpaRepository<StudentImportRow, UUID> {
    List<StudentImportRow> findByBatchIdOrderByRowNumber(UUID batchId);
    List<StudentImportRow> findByBatchIdAndOutcomeOrderByRowNumber(UUID batchId, String outcome);
}
