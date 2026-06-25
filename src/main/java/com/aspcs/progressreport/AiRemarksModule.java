package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

// ─── DTO ───────────────────────────────────────────────────────
class RemarksRequest {
    public UUID studentId;
    public String studentFirstName;   // used to personalize the remark
    public BigDecimal attendancePct;
    public BigDecimal behaviourOverall;   // 1-5 scale
    public String overallPerformance;     // OUTSTANDING/EXCELLENT/GOOD/AVERAGE/NEEDS_IMPROVEMENT
}

class RemarksResponse {
    public String remarks;
    RemarksResponse(String remarks) { this.remarks = remarks; }
}

// ─── Service ───────────────────────────────────────────────────
// Deliberately rule-based rather than calling an external LLM API:
// it's free, instant, fully deterministic (so a teacher editing one
// student's remark never gets a surprising rewrite on resubmit), and
// needs no new vendor credentials. Teachers can freely edit the
// generated text before saving — this is a starting draft, not a
// locked-in output.
@Service
class AiRemarksService {

    public String generate(RemarksRequest req) {
        String name = (req.studentFirstName != null && !req.studentFirstName.isBlank())
                ? req.studentFirstName
                : "The student";

        String performance = req.overallPerformance != null
                ? req.overallPerformance
                : inferPerformance(req);

        boolean lowAttendance = req.attendancePct != null
                && req.attendancePct.compareTo(new BigDecimal("75")) < 0;

        return switch (performance) {
            case "OUTSTANDING" -> name + " has demonstrated outstanding academic performance during " +
                    "this reporting cycle. Attendance and classroom participation are highly commendable, " +
                    "and this consistency should continue to be encouraged.";

            case "EXCELLENT" -> name + " has demonstrated excellent academic performance during this " +
                    "reporting cycle. " + (lowAttendance
                        ? "Improving attendance would help sustain this momentum."
                        : "Participation and attendance are highly commendable.");

            case "GOOD" -> name + " has shown good progress this reporting cycle, with solid " +
                    "performance across most areas. " + (lowAttendance
                        ? "More regular attendance would support further improvement."
                        : "Continued consistency will help build on this foundation.");

            case "AVERAGE" -> name + " is making steady progress. Continued practice and more " +
                    "regular participation will help improve academic outcomes" +
                    (lowAttendance ? ", and attendance in particular needs attention." : ".");

            default -> name + " would benefit from greater focus on attendance, homework completion, " +
                    "and classroom engagement. With consistent support at home and school, meaningful " +
                    "improvement is achievable next cycle.";
        };
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
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','TEACHER')")
    public ResponseEntity<ApiResponse<RemarksResponse>> generate(@RequestBody RemarksRequest req) {
        return ResponseEntity.ok(ApiResponse.ok(new RemarksResponse(service.generate(req))));
    }
}
