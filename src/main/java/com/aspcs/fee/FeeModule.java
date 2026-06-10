package com.aspcs.fee;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

// ─── Fee Category Entity ─────────────────────────────────────
@Entity @Table(name = "fee_categories")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class FeeCategory {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, unique = true) private String name;
    private String description;
    @Column(name = "is_mandatory") private boolean mandatory;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Fee Structure Entity ────────────────────────────────────
@Entity @Table(name = "fee_structures")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class FeeStructure {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "class_name", nullable = false) private String className;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "session_id")  private UUID sessionId;
    @Column(nullable = false) private BigDecimal amount;
    @Column(name = "due_day") private int dueDay;
    private String frequency;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Fee Payment Entity ──────────────────────────────────────
@Entity @Table(name = "fee_payments")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class FeePayment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "student_id")  private UUID studentId;
    @Column(name = "session_id")  private UUID sessionId;
    @Column(name = "category_id") private UUID categoryId;
    @Column(name = "receipt_no", nullable = false, unique = true) private String receiptNo;
    @Column(nullable = false) private BigDecimal amount;
    @Column(name = "late_fine") private BigDecimal lateFine;
    @Column(name = "total_amount") private BigDecimal totalAmount;
    @Column(name = "payment_date") private LocalDate paymentDate;
    @Column(name = "payment_method") private String paymentMethod;
    @Column(name = "transaction_id") private String transactionId;
    private String month;
    private String remarks;
    @Column(name = "collected_by") private UUID collectedBy;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── DTOs ────────────────────────────────────────────────────
class RecordPaymentRequest {
    @NotNull public UUID studentId;
    @NotNull public UUID categoryId;
    @NotNull public UUID sessionId;
    @NotNull public BigDecimal amount;
    public BigDecimal lateFine;
    public String paymentMethod;
    public String transactionId;
    public String month;
    public String remarks;
}

// ─── Repositories ────────────────────────────────────────────
interface FeeCategoryRepository extends JpaRepository<FeeCategory, UUID> {}

interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {
    List<FeeStructure> findByClassNameAndSessionId(String className, UUID sessionId);
}

interface FeePaymentRepository extends JpaRepository<FeePayment, UUID> {
    Page<FeePayment> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable p);
    Page<FeePayment> findAllByOrderByCreatedAtDesc(Pageable p);

    @Query("SELECT SUM(fp.totalAmount) FROM FeePayment fp WHERE fp.paymentDate BETWEEN :from AND :to")
    BigDecimal sumByDateRange(
            @org.springframework.data.repository.query.Param("from") LocalDate from,
            @org.springframework.data.repository.query.Param("to")   LocalDate to);

    long countByPaymentDate(LocalDate date);
}

// ─── Service ─────────────────────────────────────────────────
@Service @RequiredArgsConstructor
class FeeService {
    private final FeeCategoryRepository  catRepo;
    private final FeeStructureRepository structRepo;
    private final FeePaymentRepository   payRepo;

    public List<FeeCategory> getCategories() { return catRepo.findAll(); }

    public List<FeeStructure> getStructureForClass(String className, UUID sessionId) {
        return structRepo.findByClassNameAndSessionId(className, sessionId);
    }

    public Page<FeePayment> getPaymentsForStudent(UUID studentId, int page, int size) {
        return payRepo.findByStudentIdOrderByCreatedAtDesc(studentId, PageRequest.of(page, size));
    }

    public Page<FeePayment> getAllPayments(int page, int size) {
        return payRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    public FeePayment recordPayment(RecordPaymentRequest req) {
        BigDecimal fine  = req.lateFine != null ? req.lateFine : BigDecimal.ZERO;
        BigDecimal total = req.amount.add(fine);

        FeePayment payment = FeePayment.builder()
                .studentId(req.studentId).categoryId(req.categoryId)
                .sessionId(req.sessionId)
                .receiptNo(generateReceiptNo())
                .amount(req.amount).lateFine(fine).totalAmount(total)
                .paymentDate(LocalDate.now())
                .paymentMethod(req.paymentMethod != null ? req.paymentMethod : "CASH")
                .transactionId(req.transactionId)
                .month(req.month).remarks(req.remarks)
                .build();
        return payRepo.save(payment);
    }

    public java.util.Map<String, Object> getDashboardStats() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        BigDecimal monthTotal = payRepo.sumByDateRange(monthStart, today);
        BigDecimal todayTotal = payRepo.sumByDateRange(today, today);
        return java.util.Map.of(
            "totalPaymentsToday",   payRepo.countByPaymentDate(today),
            "totalAmountToday",     todayTotal   != null ? todayTotal   : BigDecimal.ZERO,
            "totalAmountThisMonth", monthTotal   != null ? monthTotal   : BigDecimal.ZERO
        );
    }

    private String generateReceiptNo() {
        return "ASPCS/FEE/" + LocalDate.now().getYear() + "/" +
               String.format("%06d", payRepo.count() + 1);
    }
}

// ─── Controller ──────────────────────────────────────────────
@RestController @RequestMapping("/fees") @RequiredArgsConstructor
class FeeController {
    private final FeeService service;

    @GetMapping("/categories")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<List<FeeCategory>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.ok(service.getCategories()));
    }

    @GetMapping("/structure")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<List<FeeStructure>>> getStructure(
            @RequestParam String className,
            @RequestParam UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getStructureForClass(className, sessionId)));
    }

    @GetMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<FeePayment>>> getAllPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAllPayments(page, size)));
    }

    @GetMapping("/payments/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<FeePayment>>> getStudentPayments(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getPaymentsForStudent(studentId, page, size)));
    }

    @PostMapping("/payments")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<FeePayment>> recordPayment(
            @RequestBody RecordPaymentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(service.recordPayment(req), "Payment recorded successfully"));
    }

    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> getDashboard() {
        return ResponseEntity.ok(ApiResponse.ok(service.getDashboardStats()));
    }
}
