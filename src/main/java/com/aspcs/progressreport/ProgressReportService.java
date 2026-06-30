package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import com.aspcs.student.Student;
import com.aspcs.student.StudentRepository;
import com.aspcs.academic.Subject;
import com.aspcs.academic.SubjectRepository;
import com.aspcs.upload.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

// ─── DTOs ──────────────────────────────────────────────────────
// Outcome for a single student's report generation + dispatch attempt.
class ReportDispatchOutcome {
    public UUID studentId;
    public String studentName;
    public UUID reportId; // set only if pdfGenerated is true; frontend uses this to build the download link
    public boolean pdfGenerated;
    public boolean emailSent;
    public boolean whatsappSent;
    public String error; // non-null only if pdfGenerated is false (a hard failure)
}

class DispatchRequest {
    public UUID cycleId;
    public String className;          // required if studentIds is empty (whole-class send)
    public String section;            // optional, narrows the class send
    public List<UUID> studentIds;     // if provided, sends only these students
    public boolean sendEmail = true;
    public boolean sendWhatsApp = true;
}

// ─── Service ───────────────────────────────────────────────────
@Slf4j
@Service
@RequiredArgsConstructor
class ProgressReportService {

    private final StudentAssessmentRepository assessmentRepo;
    private final AssessmentSubjectRepository assessmentSubjectRepo;
    private final ProgressReportRepository reportRepo;
    private final StudentRepository studentRepo;
    private final SubjectRepository subjectRepo;
    private final ReportingCycleRepository cycleRepo;
    private final PdfGenerationService pdfService;
    private final CloudinaryService cloudinaryService;
    private final EmailDispatchService emailService;
    private final WhatsAppNotificationService whatsAppService;
    private final CommunicationLogService logService;

    @Value("${app.frontend.base-url:https://aspcspatna.ac.in}")
    private String frontendBaseUrl;

    @Value("${app.backend.base-url:https://aspcs-backend-production.up.railway.app/api/v1}")
    private String backendBaseUrl;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    // ─── Single-student generate + dispatch ───────────────────
    public ReportDispatchOutcome generateAndDispatch(UUID assessmentId, boolean sendEmail, boolean sendWhatsApp) {
        StudentAssessment assessment = assessmentRepo.findById(assessmentId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Assessment not found"));

        Student student = studentRepo.findById(assessment.getStudentId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Student not found"));

        ReportingCycle cycle = cycleRepo.findById(assessment.getCycleId())
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Reporting cycle not found"));

        ReportDispatchOutcome outcome = new ReportDispatchOutcome();
        outcome.studentId = student.getId();
        outcome.studentName = student.getFullName();

        try {
            ProgressReport report = generateReport(assessment, student, cycle);
            outcome.pdfGenerated = true;
            outcome.reportId = report.getId();

            if (sendEmail) {
                EmailResult emailResult = dispatchEmail(report, assessment, student, cycle);
                outcome.emailSent = emailResult.success();
            }
            if (sendWhatsApp) {
                WhatsAppResult waResult = dispatchWhatsApp(report, assessment, student, cycle);
                outcome.whatsappSent = waResult.success();
            }
        } catch (Exception e) {
            log.error("Report generation failed for student {}: {}", student.getId(), e.getMessage(), e);
            outcome.pdfGenerated = false;
            outcome.error = e.getMessage();
        }
        return outcome;
    }

    // ─── Class-wide / multi-student dispatch ───────────────────
    // Only SUBMITTED (or already-LOCKED, i.e. previously sent) assessments
    // are eligible — a DRAFT is by definition unfinished, and emailing an
    // incomplete report to a parent would be a real problem, not just a
    // UI nicety. Explicitly-requested studentIds still go through this
    // same filter rather than bypassing it.
    public List<ReportDispatchOutcome> dispatchBulk(DispatchRequest req) {
        List<StudentAssessment> candidates;
        if (req.studentIds != null && !req.studentIds.isEmpty()) {
            candidates = req.studentIds.stream()
                    .map(sid -> assessmentRepo.findByCycleIdAndStudentId(req.cycleId, sid))
                    .filter(Optional::isPresent).map(Optional::get)
                    .toList();
        } else if (req.section != null && !req.section.isBlank()) {
            candidates = assessmentRepo.findByCycleIdAndClassNameAndSection(req.cycleId, req.className, req.section);
        } else {
            candidates = assessmentRepo.findByCycleIdAndClassName(req.cycleId, req.className);
        }

        List<StudentAssessment> targets = candidates.stream()
                .filter(a -> "SUBMITTED".equals(a.getStatus()) || "LOCKED".equals(a.getStatus()))
                .toList();

        List<ReportDispatchOutcome> outcomes = new ArrayList<>();
        for (StudentAssessment a : targets) {
            outcomes.add(generateAndDispatch(a.getId(), req.sendEmail, req.sendWhatsApp));
        }
        return outcomes;
    }

    // ─── Core PDF generation + Cloudinary upload + progress_reports row ───
    private ProgressReport generateReport(StudentAssessment assessment, Student student, ReportingCycle cycle)
            throws Exception {

        List<AssessmentSubject> scores = assessmentSubjectRepo.findByAssessmentId(assessment.getId());
        Map<UUID, Subject> subjectsById = subjectRepo.findAllById(
                scores.stream().map(AssessmentSubject::getSubjectId).toList()
        ).stream().collect(Collectors.toMap(Subject::getId, s -> s));

        List<SubjectRow> subjectRows = scores.stream()
                .map(s -> {
                    Subject subj = subjectsById.get(s.getSubjectId());
                    String name = subj != null ? subj.getName() : "Unknown Subject";
                    String value = s.getMarks() != null
                            ? s.getMarks().setScale(0, RoundingMode.HALF_UP) + " / " + (subj != null ? subj.getMaxMarks() : 100)
                            : s.getRating();
                    return new SubjectRow(name, value, s.getRemarks());
                })
                .sorted(Comparator.comparing(SubjectRow::subjectName))
                .toList();

        String qrCode = UUID.randomUUID().toString();
        // QR scans go directly to the PDF — no login required, no intermediate page.
        String verificationUrl = backendBaseUrl + "/progress-reports/reports/verify/" + qrCode + "/pdf";

        String attendanceDisplay = assessment.getAttendancePct() != null
                ? assessment.getAttendancePct() + "%" : "-";
        String behaviourDisplay = assessment.getBehaviourOverall() != null
                ? assessment.getBehaviourOverall() + " / 5" : "-";
        String cycleDateRange = cycle.getStartDate().format(DATE_FMT) + " - " + cycle.getEndDate().format(DATE_FMT);

        ReportPdfData pdfData = new ReportPdfData(
                student.getFullName(),
                student.getAdmissionNo(),
                assessment.getClassName(),
                assessment.getSection(),
                cycle.getName(),
                cycleDateRange,
                student.getPhotoUrl(),
                assessment.getWorkingDays(),
                assessment.getPresentDays(),
                attendanceDisplay,
                subjectRows,
                assessment.getDisciplineScore(), assessment.getHomeworkScore(), assessment.getParticipationScore(),
                assessment.getPunctualityScore(), assessment.getCommunicationScore(), assessment.getTeamworkScore(),
                behaviourDisplay,
                assessment.getTeacherRemarks(),
                assessment.getPrincipalRemarks(),
                assessment.getOverallPerformance(),
                LocalDate.now().format(DATE_FMT),
                verificationUrl
        );

        byte[] pdfBytes = pdfService.generate(pdfData);

        String publicId = "report_" + student.getAdmissionNo() + "_" + cycle.getId();
        String pdfUrl = cloudinaryService.uploadBytes(pdfBytes, "aspcs/progress-reports", publicId);

        // Replace any prior report for this assessment (e.g. regenerated after a correction).
        ProgressReport report = reportRepo.findByAssessmentId(assessment.getId()).orElseGet(ProgressReport::new);
        report.setAssessmentId(assessment.getId());
        report.setCycleId(cycle.getId());
        report.setStudentId(student.getId());
        report.setPdfUrl(pdfUrl);
        report.setQrCode(qrCode);
        return reportRepo.save(report);
    }

    private EmailResult dispatchEmail(ProgressReport report, StudentAssessment assessment, Student student, ReportingCycle cycle) {
        EmailResult result;
        try {
            byte[] pdfBytes = downloadPdf(report.getPdfUrl());
            result = emailService.sendProgressReport(
                    student.getGuardianEmail(), student.getFullName(), assessment.getClassName(),
                    assessment.getSection(), cycle.getName(), pdfBytes,
                    student.getFullName().replace(" ", "_") + "_Progress_Report.pdf");
        } catch (Exception e) {
            result = new EmailResult(false, null, "Could not retrieve generated PDF: " + e.getMessage());
        }
        logService.logEmail(report.getId(), student.getId(), student.getGuardianEmail(),
                "ASPCS Progress Report | " + student.getFullName(), result);
        return result;
    }

    private WhatsAppResult dispatchWhatsApp(ProgressReport report, StudentAssessment assessment, Student student, ReportingCycle cycle) {
        String cycleDateRange = cycle.getStartDate().format(DATE_FMT) + " - " + cycle.getEndDate().format(DATE_FMT);
        String attendanceDisplay = assessment.getAttendancePct() != null ? assessment.getAttendancePct() + "%" : "-";
        String performanceDisplay = assessment.getOverallPerformance() != null
                ? assessment.getOverallPerformance().replace("_", " ") : "-";

        WhatsAppResult result = whatsAppService.sendProgressReportNotification(
                student.getGuardianPhone(), student.getFullName(), assessment.getClassName(),
                assessment.getSection(), cycleDateRange, attendanceDisplay, performanceDisplay);

        logService.logWhatsApp(report.getId(), student.getId(), student.getGuardianPhone(),
                "Progress report notification for " + cycle.getName(), result);
        return result;
    }

    // Cloudinary URLs are public HTTPS; a short-timeout fetch back is simpler
    // and avoids holding the PDF bytes in memory between generation and send
    // when this runs as part of a larger batch.
    private byte[] downloadPdf(String url) throws java.io.IOException {
        java.net.URLConnection conn = new java.net.URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        try (var in = conn.getInputStream()) {
            return in.readAllBytes();
        }
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/dispatch")
@RequiredArgsConstructor
class ProgressReportDispatchController {

    private final ProgressReportService service;

    @PostMapping("/{assessmentId}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<ReportDispatchOutcome>> generateAndDispatch(
            @PathVariable UUID assessmentId,
            @RequestParam(defaultValue = "true") boolean email,
            @RequestParam(defaultValue = "true") boolean whatsapp) {
        return ResponseEntity.ok(ApiResponse.ok(service.generateAndDispatch(assessmentId, email, whatsapp)));
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<List<ReportDispatchOutcome>>> dispatchBulk(
            @RequestBody DispatchRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(service.dispatchBulk(req), "Dispatch complete"));
    }
}
