package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

// ─── DTOs ──────────────────────────────────────────────────────
class RemarksRequest {
    public String studentFirstName;
    public BigDecimal attendancePct;
    public BigDecimal behaviourOverall;
    public String overallPerformance;
    // Per-subject data: name -> rating/marks string (e.g. "EXCELLENT" or "85")
    public Map<String, String> subjectPerformance;
}

class RemarksResponse {
    public String remarks;
    RemarksResponse(String remarks) { this.remarks = remarks; }
}

// ─── Service ───────────────────────────────────────────────────
@Service
class AiRemarksService {

    private static final Set<String> HIGH = Set.of("OUTSTANDING", "EXCELLENT");
    private static final Set<String> LOW  = Set.of("AVERAGE", "NEEDS_IMPROVEMENT");

    public String generate(RemarksRequest req) {
        String name = (req.studentFirstName != null && !req.studentFirstName.isBlank())
                ? req.studentFirstName : "The student";

        String performance = req.overallPerformance != null
                ? req.overallPerformance : inferPerformance(req);

        boolean lowAttendance = req.attendancePct != null
                && req.attendancePct.compareTo(new BigDecimal("75")) < 0;

        // Categorize subjects into strong and weak
        List<String> strongSubjects = new ArrayList<>();
        List<String> weakSubjects = new ArrayList<>();
        if (req.subjectPerformance != null) {
            for (Map.Entry<String, String> e : req.subjectPerformance.entrySet()) {
                String val = e.getValue();
                if (val == null || val.isBlank()) continue;
                String upper = val.toUpperCase().trim();
                if (HIGH.contains(upper) || isHighMarks(val)) {
                    strongSubjects.add(e.getKey());
                } else if (LOW.contains(upper) || isLowMarks(val)) {
                    weakSubjects.add(e.getKey());
                }
            }
        }

        StringBuilder sb = new StringBuilder();

        // Opening line based on overall performance
        switch (performance) {
            case "OUTSTANDING" -> sb.append(name).append(" has demonstrated outstanding academic performance this reporting cycle.");
            case "EXCELLENT"   -> sb.append(name).append(" has shown excellent progress across the curriculum this reporting cycle.");
            case "GOOD"        -> sb.append(name).append(" has shown good and steady progress this reporting cycle.");
            case "AVERAGE"     -> sb.append(name).append(" is making gradual progress and showing effort this reporting cycle.");
            default            -> sb.append(name).append(" has scope for significant improvement this reporting cycle.");
        }

        // Subject-specific praise
        if (!strongSubjects.isEmpty()) {
            sb.append(" Particularly strong performance in ");
            sb.append(joinNaturally(strongSubjects));
            sb.append(".");
        }

        // Subject-specific improvement areas
        if (!weakSubjects.isEmpty()) {
            sb.append(" Additional practice and focus would help improve results in ");
            sb.append(joinNaturally(weakSubjects));
            sb.append(".");
        }

        // Attendance
        if (req.attendancePct != null) {
            if (lowAttendance) {
                sb.append(" Attendance at ").append(req.attendancePct).append("% needs improvement — regular attendance is key to sustained progress.");
            } else if (req.attendancePct.compareTo(new BigDecimal("90")) >= 0) {
                sb.append(" Attendance is commendable.");
            }
        }

        // Behaviour
        if (req.behaviourOverall != null) {
            double bv = req.behaviourOverall.doubleValue();
            if (bv >= 4.0) {
                sb.append(" Classroom discipline and participation are praiseworthy.");
            } else if (bv < 3.0) {
                sb.append(" Improved discipline and active participation in class activities would support better learning outcomes.");
            }
        }

        // Closing encouragement
        switch (performance) {
            case "OUTSTANDING", "EXCELLENT" -> sb.append(" Keep up the excellent work!");
            case "GOOD" -> sb.append(" With continued consistency, even better results are within reach.");
            default -> sb.append(" With consistent effort at home and school, meaningful improvement is achievable.");
        }

        return sb.toString();
    }

    private String joinNaturally(List<String> items) {
        if (items.size() == 1) return items.get(0);
        if (items.size() == 2) return items.get(0) + " and " + items.get(1);
        return String.join(", ", items.subList(0, items.size() - 1)) + ", and " + items.get(items.size() - 1);
    }

    private boolean isHighMarks(String val) {
        try { return Double.parseDouble(val) >= 80; } catch (Exception e) { return false; }
    }

    private boolean isLowMarks(String val) {
        try { return Double.parseDouble(val) < 50; } catch (Exception e) { return false; }
    }

    private String inferPerformance(RemarksRequest req) {
        if (req.behaviourOverall == null && req.attendancePct == null) return "AVERAGE";
        double score = 0; int factors = 0;
        if (req.behaviourOverall != null) { score += (req.behaviourOverall.doubleValue() / 5.0) * 100; factors++; }
        if (req.attendancePct != null) { score += req.attendancePct.doubleValue(); factors++; }
        double avg = factors > 0 ? score / factors : 0;
        if (avg >= 90) return "OUTSTANDING";
        if (avg >= 75) return "EXCELLENT";
        if (avg >= 60) return "GOOD";
        if (avg >= 45) return "AVERAGE";
        return "NEEDS_IMPROVEMENT";
    }
}

// ─── Controller ────────────────────────────────────────────────
@RestController
@RequestMapping("/progress-reports/ai-remarks")
@RequiredArgsConstructor
class AiRemarksController {

    private final AiRemarksService service;

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','EDITOR','TEACHER')")
    public ResponseEntity<ApiResponse<RemarksResponse>> generate(@RequestBody RemarksRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(new RemarksResponse(service.generate(req))));
    }
}
