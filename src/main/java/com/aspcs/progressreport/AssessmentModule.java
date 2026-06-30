package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import com.aspcs.student.Student;
import com.aspcs.student.StudentRepository;
import com.aspcs.academic.Subject;
import com.aspcs.academic.SubjectRepository;
import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

// ─── Entities ──────────────────────────────────────────────────
@Entity
@Table(name = "student_assessments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class StudentAssessment {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;

    @Column(name = "cycle_id", nullable = false)   private UUID cycleId;
    @Column(name = "student_id", nullable = false) private UUID studentId;
    @Column(name = "class_name", nullable = false) private String className;
    private String section;

    @Column(name = "working_days") private Integer workingDays;
    @Column(name = "present_days") private Integer presentDays;
    @Column(name = "attendance_pct") private BigDecimal attendancePct;

    @Column(name = "discipline_score")   private Short disciplineScore;
    @Column(name = "homework_score")     private Short homeworkScore;
    @Column(name = "participation_score") private Short participationScore;
    @Column(name = "punctuality_score")  private Short punctualityScore;
    @Column(name = "communication_score") private Short communicationScore;
    @Column(name = "teamwork_score")     private Short teamworkScore;
    @Column(name = "behaviour_overall")  private BigDecimal behaviourOverall;

    @Column(name = "teacher_remarks", columnDefinition = "TEXT")   private String teacherRemarks;
    @Column(name = "principal_remarks", columnDefinition = "TEXT") private String principalRemarks;

    @Column(name = "overall_performance") private String overallPerformance;

    @Column(nullable = false) private String status = "DRAFT"; // DRAFT, SUBMITTED, LOCKED
    @Column(name = "submitted_by") private UUID submittedBy;
    @Column(name = "submitted_at") private LocalDateTime submittedAt;

    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;

    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

@Entity
@Table(name = "assessment_subjects")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
class AssessmentSubject {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(name = "assessment_id", nullable = false) private UUID assessmentId;
    @Column(name = "subject_id", nullable = false)    private UUID subjectId;
    private BigDecimal marks;   // MARKS mode
    private String rating;      // RATING mode
    @Column(columnDefinition = "TEXT") private String remarks; // per-subject feedback
    @Column(name = "created_at", updatable = false) private LocalDateTime createdAt;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @PrePersist protected void onCreate() { createdAt = updatedAt = LocalDateTime.now(); }
    @PreUpdate  protected void onUpdate() { updatedAt = LocalDateTime.now(); }
}

// ─── DTOs ──────────────────────────────────────────────────────
// One row of the spreadsheet grid, submitted per-student.
class AssessmentRowRequest {
    @NotNull public UUID studentId;
    public Integer workingDays;
    public Integer presentDays;

    public Short disciplineScore;
    public Short homeworkScore;
    public Short participationScore;
    public Short punctualityScore;
    public Short communicationScore;
    public Short teamworkScore;

    public String teacherRemarks;

    // subjectId -> marks (MARKS mode) or rating string (RATING mode)
    public Map<UUID, BigDecimal> subjectMarks;
    public Map<UUID, String> subjectRatings;
    // subjectId -> per-subject feedback/remarks
    public Map<UUID, String> subjectRemarks;
}

// Bulk save payload: the whole grid for one class-section-cycle at once.
class BulkAssessmentRequest {
    @NotNull public UUID cycleId;
    @NotNull public String className;
    public String section;
    @NotNull public List<AssessmentRowRequest> rows;
    public boolean submit; // false = save as draft, true = mark SUBMITTED
}

// What the grid screen loads to render itself.
class AssessmentGridResponse {
    public List<GridSubjectColumn> subjectColumns;
    public List<GridRow> rows;
    public String performanceMode; // MARKS or RATING
}

class GridSubjectColumn {
    public UUID subjectId;
    public String subjectName;
    public String subjectCode;
    GridSubjectColumn(UUID id, String name, String code) { subjectId = id; subjectName = name; subjectCode = code; }
}

class GridRow {
    public UUID studentId;
    public String rollNo;
    public String fullName;
    public UUID assessmentId;        // null if not yet created for this cycle
    public String status;            // DRAFT, SUBMITTED, LOCKED, or null if none yet
    public Integer workingDays;
    public Integer presentDays;
    public BigDecimal attendancePct;
    public Short disciplineScore, homeworkScore, participationScore,
                 punctualityScore, communicationScore, teamworkScore;
    public String teacherRemarks;
    public Map<UUID, BigDecimal> subjectMarks   = new HashMap<>();
    public Map<UUID, String>     subjectRatings = new HashMap<>();
    public Map<UUID, String>     subjectRemarks = new HashMap<>();
}

// ─── Repositories ──────────────────────────────────────────────
interface StudentAssessmentRepository extends JpaRepository<StudentAssessment, UUID> {
    Optional<StudentAssessment> findByCycleIdAndStudentId(UUID cycleId, UUID studentId);
    List<StudentAssessment> findByCycleIdAndClassNameAndSection(UUID cycleId, String className, String section);
    List<StudentAssessment> findByCycleIdAndClassName(UUID cycleId, String className);
    long countByCycleId(UUID cycleId);
    long countByCycleIdAndStatus(UUID cycleId, String status);
}

interface AssessmentSubjectRepository extends JpaRepository<AssessmentSubject, UUID> {
    List<AssessmentSubject> findByAssessmentId(UUID assessmentId);
    List<AssessmentSubject> findByAssessmentIdIn(List<UUID> assessmentIds);
    void deleteByAssessmentId(UUID assessmentId);
}

// ─── Service ───────────────────────────────────────────────────
@Service
@RequiredArgsConstructor
class AssessmentService {

    private final StudentAssessmentRepository assessmentRepo;
    private final AssessmentSubjectRepository subjectScoreRepo;
    private final StudentRepository studentRepo;
    private final ClassSubjectMapRepository classSubjectMapRepo;
    private final ReportingCycleRepository cycleRepo;
    private final AssessmentSettingsService settingsService;
    private final SubjectRepository subjectRepo;
    private final TeacherClassAssignmentService teacherAssignmentService;

    // Admins/editors can access any class. A TEACHER must be assigned to the
    // specific class-section (as subject teacher or class teacher) to view
    // or submit assessments for it.
    private void requireAccess(com.aspcs.auth.entity.AdminUser user, String className, String section) {
        if (user.getRole() != com.aspcs.auth.entity.AdminUser.Role.TEACHER) return;
        boolean allowed = teacherAssignmentService.canAccess(user.getTeacherId(), className, section);
        if (!allowed) {
            throw new org.springframework.security.access.AccessDeniedException(
                    "You are not assigned to " + className + (section != null ? " - " + section : ""));
        }
    }

    // ─── Grid load ───────────────────────────────────────────
    public AssessmentGridResponse getGrid(com.aspcs.auth.entity.AdminUser user, UUID cycleId, String className, String section) {
        requireAccess(user, className, section);
        cycleRepo.findById(cycleId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Reporting cycle not found"));

        List<ClassSubjectMap> subjectMaps = classSubjectMapRepo.findByClassNameOrderByCreatedAt(className);
        List<UUID> subjectIds = subjectMaps.stream().map(ClassSubjectMap::getSubjectId).toList();
        Map<UUID, Subject> subjectsById = subjectRepo.findAllById(subjectIds).stream()
                .collect(Collectors.toMap(Subject::getId, s -> s));

        List<GridSubjectColumn> columns = subjectMaps.stream()
                .map(m -> subjectsById.get(m.getSubjectId()))
                .filter(Objects::nonNull)
                .map(s -> new GridSubjectColumn(s.getId(), s.getName(), s.getCode()))
                .toList();

        List<Student> students = section != null && !section.isBlank()
                ? studentRepo.findByCurrentClassAndSectionAndStatusOrderByFullNameAsc(className, section, "ACTIVE")
                : studentRepo.findByCurrentClassOrderByFullNameAsc(className, org.springframework.data.domain.Pageable.unpaged()).getContent();

        List<StudentAssessment> existing = section != null && !section.isBlank()
                ? assessmentRepo.findByCycleIdAndClassNameAndSection(cycleId, className, section)
                : assessmentRepo.findByCycleIdAndClassName(cycleId, className);

        Map<UUID, StudentAssessment> byStudent = existing.stream()
                .collect(Collectors.toMap(StudentAssessment::getStudentId, a -> a));

        List<UUID> existingIds = existing.stream().map(StudentAssessment::getId).toList();
        Map<UUID, List<AssessmentSubject>> scoresByAssessment = existingIds.isEmpty()
                ? Map.of()
                : subjectScoreRepo.findByAssessmentIdIn(existingIds).stream()
                    .collect(Collectors.groupingBy(AssessmentSubject::getAssessmentId));

        List<GridRow> rows = students.stream().map(s -> {
            GridRow row = new GridRow();
            row.studentId = s.getId();
            row.rollNo = s.getRollNo();
            row.fullName = s.getFullName();

            StudentAssessment a = byStudent.get(s.getId());
            if (a != null) {
                row.assessmentId = a.getId();
                row.status = a.getStatus();
                row.workingDays = a.getWorkingDays();
                row.presentDays = a.getPresentDays();
                row.attendancePct = a.getAttendancePct();
                row.disciplineScore = a.getDisciplineScore();
                row.homeworkScore = a.getHomeworkScore();
                row.participationScore = a.getParticipationScore();
                row.punctualityScore = a.getPunctualityScore();
                row.communicationScore = a.getCommunicationScore();
                row.teamworkScore = a.getTeamworkScore();
                row.teacherRemarks = a.getTeacherRemarks();
                for (AssessmentSubject sc : scoresByAssessment.getOrDefault(a.getId(), List.of())) {
                    if (sc.getMarks() != null) row.subjectMarks.put(sc.getSubjectId(), sc.getMarks());
                    if (sc.getRating() != null) row.subjectRatings.put(sc.getSubjectId(), sc.getRating());
                    if (sc.getRemarks() != null) row.subjectRemarks.put(sc.getSubjectId(), sc.getRemarks());
                }
            }
            return row;
        }).toList();

        AssessmentGridResponse resp = new AssessmentGridResponse();
        resp.subjectColumns = columns;
        resp.rows = rows;
        resp.performanceMode = settingsService.get().getPerformanceMode();
        return resp;
    }

    // ─── Bulk save (draft or submit) ──────────────────────────
    public List<StudentAssessment> bulkSave(BulkAssessmentRequest req, com.aspcs.auth.entity.AdminUser actingUser) {
        requireAccess(actingUser, req.className, req.section);
        ReportingCycle cycle = cycleRepo.findById(req.cycleId)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Reporting cycle not found"));
        if (!"OPEN".equals(cycle.getStatus())) {
            throw new IllegalArgumentException("Cannot submit assessments for a cycle that is not OPEN");
        }

        boolean marksMode = settingsService.isMarksMode();
        List<StudentAssessment> saved = new ArrayList<>();

        for (AssessmentRowRequest row : req.rows) {
            StudentAssessment a = assessmentRepo.findByCycleIdAndStudentId(req.cycleId, row.studentId)
                    .orElseGet(StudentAssessment::new);

            if (a.getId() != null && "LOCKED".equals(a.getStatus())) {
                throw new IllegalArgumentException("Assessment for this student is locked and cannot be edited");
            }

            a.setCycleId(req.cycleId);
            a.setStudentId(row.studentId);
            a.setClassName(req.className);
            a.setSection(req.section);
            a.setWorkingDays(row.workingDays);
            a.setPresentDays(row.presentDays);
            a.setAttendancePct(computeAttendancePct(row.workingDays, row.presentDays));

            a.setDisciplineScore(row.disciplineScore);
            a.setHomeworkScore(row.homeworkScore);
            a.setParticipationScore(row.participationScore);
            a.setPunctualityScore(row.punctualityScore);
            a.setCommunicationScore(row.communicationScore);
            a.setTeamworkScore(row.teamworkScore);
            a.setBehaviourOverall(computeBehaviourOverall(row));

            a.setTeacherRemarks(row.teacherRemarks);
            a.setOverallPerformance(computeOverallPerformance(a, row, marksMode));

            if (req.submit) {
                a.setStatus("SUBMITTED");
                a.setSubmittedBy(actingUser.getId());
                a.setSubmittedAt(LocalDateTime.now());
            } else if (a.getStatus() == null) {
                a.setStatus("DRAFT");
            }

            StudentAssessment savedAssessment = assessmentRepo.save(a);

            // Replace subject scores for this assessment
            subjectScoreRepo.deleteByAssessmentId(savedAssessment.getId());
            Map<UUID, BigDecimal> marksMap = row.subjectMarks != null ? row.subjectMarks : Map.of();
            Map<UUID, String> ratingsMap = row.subjectRatings != null ? row.subjectRatings : Map.of();
            Map<UUID, String> remarksMap = row.subjectRemarks != null ? row.subjectRemarks : Map.of();
            Set<UUID> allSubjectIds = new HashSet<>();
            allSubjectIds.addAll(marksMap.keySet());
            allSubjectIds.addAll(ratingsMap.keySet());
            allSubjectIds.addAll(remarksMap.keySet());

            for (UUID subjectId : allSubjectIds) {
                AssessmentSubject sc = new AssessmentSubject();
                sc.setAssessmentId(savedAssessment.getId());
                sc.setSubjectId(subjectId);
                sc.setMarks(marksMap.get(subjectId));
                sc.setRating(ratingsMap.get(subjectId));
                sc.setRemarks(remarksMap.get(subjectId));
                subjectScoreRepo.save(sc);
            }

            saved.add(savedAssessment);
        }
        return saved;
    }

    private BigDecimal computeAttendancePct(Integer workingDays, Integer presentDays) {
        if (workingDays == null || presentDays == null || workingDays == 0) return null;
        return BigDecimal.valueOf(presentDays)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(workingDays), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal computeBehaviourOverall(AssessmentRowRequest row) {
        List<Short> scores = Arrays.asList(
                row.disciplineScore, row.homeworkScore, row.participationScore,
                row.punctualityScore, row.communicationScore, row.teamworkScore);
        List<Short> present = scores.stream().filter(Objects::nonNull).toList();
        if (present.isEmpty()) return null;
        double avg = present.stream().mapToInt(Short::intValue).average().orElse(0);
        return BigDecimal.valueOf(avg).setScale(2, RoundingMode.HALF_UP);
    }

    // Simple weighted heuristic: behaviour average + attendance band decide the
    // label. This same label is reused by the AI remarks generator so remarks
    // and the printed "overall performance" on the PDF always agree.
    private String computeOverallPerformance(StudentAssessment a, AssessmentRowRequest row, boolean marksMode) {
        BigDecimal behaviour = a.getBehaviourOverall();
        BigDecimal attendance = a.getAttendancePct();
        if (behaviour == null && attendance == null) return null;

        double score = 0;
        int factors = 0;
        if (behaviour != null) { score += (behaviour.doubleValue() / 5.0) * 100; factors++; }
        if (attendance != null) { score += attendance.doubleValue(); factors++; }
        double avg = factors > 0 ? score / factors : 0;

        if (avg >= 90) return "OUTSTANDING";
        if (avg >= 75) return "EXCELLENT";
        if (avg >= 60) return "GOOD";
        if (avg >= 45) return "AVERAGE";
        return "NEEDS_IMPROVEMENT";
    }

    public StudentAssessment getById(UUID id) {
        return assessmentRepo.findById(id)
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Assessment not found"));
    }

    public StudentAssessment lock(UUID id) {
        StudentAssessment a = getById(id);
        if (!"SUBMITTED".equals(a.getStatus())) {
            throw new IllegalArgumentException("Only a SUBMITTED assessment can be locked");
        }
        a.setStatus("LOCKED");
        return assessmentRepo.save(a);
    }

    public Map<String, Object> getCycleStats(UUID cycleId) {
        return Map.of(
            "total", assessmentRepo.countByCycleId(cycleId),
            "draft", assessmentRepo.countByCycleIdAndStatus(cycleId, "DRAFT"),
            "submitted", assessmentRepo.countByCycleIdAndStatus(cycleId, "SUBMITTED"),
            "locked", assessmentRepo.countByCycleIdAndStatus(cycleId, "LOCKED")
        );
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/assessments")
@RequiredArgsConstructor
class AssessmentController {

    private final AssessmentService service;

    @GetMapping("/grid")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<AssessmentGridResponse>> getGrid(
            @RequestParam UUID cycleId,
            @RequestParam String className,
            @RequestParam(required = false) String section,
            @AuthenticationPrincipal com.aspcs.auth.entity.AdminUser user) {
        return ResponseEntity.ok(ApiResponse.ok(service.getGrid(user, cycleId, className, section)));
    }

    @PostMapping("/bulk-save")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<List<StudentAssessment>>> bulkSave(
            @Valid @RequestBody BulkAssessmentRequest req,
            @AuthenticationPrincipal com.aspcs.auth.entity.AdminUser user) {
        List<StudentAssessment> result = service.bulkSave(req, user);
        String msg = req.submit ? "Assessments submitted" : "Draft saved";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result, msg));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<StudentAssessment>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.getById(id)));
    }

    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<StudentAssessment>> lock(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(service.lock(id), "Assessment locked"));
    }

    @GetMapping("/cycle-stats")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCycleStats(@RequestParam UUID cycleId) {
        return ResponseEntity.ok(ApiResponse.ok(service.getCycleStats(cycleId)));
    }
}
