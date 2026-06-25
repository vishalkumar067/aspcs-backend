package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

// ─── Entity ────────────────────────────────────────────────────
@Entity
@Table(name = "communication_logs")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
class CommunicationLog {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "report_id")  private UUID reportId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(nullable = false)    private String channel;   // EMAIL, WHATSAPP
    @Column(nullable = false)    private String recipient;
    private String subject;
    @Column(name = "message_preview") private String messagePreview;

    @Column(nullable = false) private String status = "PENDING"; // PENDING, SENT, DELIVERED, FAILED
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "provider_ref") private String providerRef;
    @Column(name = "sent_at") private LocalDateTime sentAt;
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;

    @PrePersist protected void onCreate() { createdAt = LocalDateTime.now(); }
}

// ─── Repository ────────────────────────────────────────────────
interface CommunicationLogRepository extends JpaRepository<CommunicationLog, UUID> {
    Page<CommunicationLog> findByStudentIdOrderByCreatedAtDesc(UUID studentId, Pageable p);
    Page<CommunicationLog> findByChannelOrderByCreatedAtDesc(String channel, Pageable p);
    Page<CommunicationLog> findAllByOrderByCreatedAtDesc(Pageable p);
    long countByChannelAndStatus(String channel, String status);
    long countByChannel(String channel);
}

// ─── Service ───────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class CommunicationLogService {

    private final CommunicationLogRepository repo;

    public CommunicationLog logEmail(UUID reportId, UUID studentId, String recipient,
                                      String subject, EmailResult result) {
        return repo.save(buildLog(reportId, studentId, "EMAIL", recipient, subject, null, result.success(),
                result.providerRef(), result.errorMessage()));
    }

    public CommunicationLog logWhatsApp(UUID reportId, UUID studentId, String recipient,
                                         String preview, WhatsAppResult result) {
        return repo.save(buildLog(reportId, studentId, "WHATSAPP", recipient, null, preview, result.success(),
                result.providerRef(), result.errorMessage()));
    }

    private CommunicationLog buildLog(UUID reportId, UUID studentId, String channel, String recipient,
                                       String subject, String preview, boolean success,
                                       String providerRef, String errorMessage) {
        return CommunicationLog.builder()
                .reportId(reportId)
                .studentId(studentId)
                .channel(channel)
                .recipient(recipient != null ? recipient : "-")
                .subject(subject)
                .messagePreview(preview)
                .status(success ? "SENT" : "FAILED")
                .providerRef(providerRef)
                .errorMessage(errorMessage)
                .sentAt(success ? LocalDateTime.now() : null)
                .build();
    }

    public Page<CommunicationLog> getAll(int page, int size, String channel) {
        Pageable p = PageRequest.of(page, size, Sort.by("createdAt").descending());
        if (channel != null && !channel.isBlank()) {
            return repo.findByChannelOrderByCreatedAtDesc(channel.toUpperCase(), p);
        }
        return repo.findAllByOrderByCreatedAtDesc(p);
    }

    public Page<CommunicationLog> getForStudent(UUID studentId, int page, int size) {
        return repo.findByStudentIdOrderByCreatedAtDesc(studentId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
    }

    public Map<String, Object> getStats() {
        return Map.of(
            "emailSent", repo.countByChannelAndStatus("EMAIL", "SENT"),
            "emailFailed", repo.countByChannelAndStatus("EMAIL", "FAILED"),
            "emailTotal", repo.countByChannel("EMAIL"),
            "whatsappSent", repo.countByChannelAndStatus("WHATSAPP", "SENT"),
            "whatsappFailed", repo.countByChannelAndStatus("WHATSAPP", "FAILED"),
            "whatsappTotal", repo.countByChannel("WHATSAPP")
        );
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/communication-logs")
@RequiredArgsConstructor
class CommunicationLogController {

    private final CommunicationLogService service;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Page<CommunicationLog>>> getAll(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channel) {
        return ResponseEntity.ok(ApiResponse.ok(service.getAll(page, size, channel)));
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<Page<CommunicationLog>>> getForStudent(
            @PathVariable UUID studentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok(service.getForStudent(studentId, page, size)));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        return ResponseEntity.ok(ApiResponse.ok(service.getStats()));
    }
}
