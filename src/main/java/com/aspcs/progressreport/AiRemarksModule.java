package com.aspcs.progressreport;

import com.aspcs.common.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

// ─── Request DTO (matches frontend payload exactly) ────────────
class RemarksRequest {
    public String studentName;
    public String studentFirstName;
    public String className;
    public String section;
    public BigDecimal attendancePct;
    public BigDecimal averageMarks;
    public String overallPerformance;
    public BehaviourScores behaviour;
    public Map<String, String> subjectPerformance;
    public String writingStyle;
}

class BehaviourScores {
    public Integer discipline;
    public Integer homework;
    public Integer participation;
    public Integer punctuality;
    public Integer communication;
    public Integer teamwork;
}

class RemarksResponse {
    public String remarks;
    RemarksResponse(String remarks) { this.remarks = remarks; }
}

// ─── Gemini API response models ────────────────────────────────
@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiResponse {
    public List<GeminiCandidate> candidates;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiCandidate {
    public GeminiContent content;
    @JsonProperty("finishReason") public String finishReason;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiContent {
    public List<GeminiPart> parts;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiPart {
    public String text;
    // Gemini 2.5+ thinking models return internal reasoning as parts
    // with thought=true. These must be filtered out — they're not the
    // actual remark, and including them produces garbled output.
    public Boolean thought;
}

// ─── Service ───────────────────────────────────────────────────
@Slf4j
@Service
class AiRemarksService {

    @Value("${app.ai.gemini-api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini-model:gemini-2.5-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    public String generate(RemarksRequest req) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "AI remarks require a Gemini API key. Get one free at aistudio.google.com and set GEMINI_API_KEY on Railway.");
        }

        String prompt = buildPrompt(req);

        try {
            String url = API_BASE + model + ":generateContent";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            // ── Generation config ──
            // thinkingBudget: 0 is critical — Gemini 2.5 Flash has thinking
            // enabled by default, and thinking tokens are counted AGAINST
            // maxOutputTokens. With the old maxOutputTokens=300, the model
            // was spending 200-800 tokens on internal reasoning, leaving
            // almost nothing for the actual remark text → truncated output.
            // Setting thinkingBudget to 0 disables thinking entirely so all
            // tokens go to the actual remark.
            Map<String, Object> thinkingConfig = new LinkedHashMap<>();
            thinkingConfig.put("thinkingBudget", 0);

            Map<String, Object> generationConfig = new LinkedHashMap<>();
            generationConfig.put("temperature", 0.95);
            generationConfig.put("maxOutputTokens", 1024);
            generationConfig.put("thinkingConfig", thinkingConfig);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("contents", List.of(
                    Map.of("parts", List.of(Map.of("text", prompt)))
            ));
            body.put("generationConfig", generationConfig);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            GeminiResponse gemini = objectMapper.readValue(response.getBody(), GeminiResponse.class);

            if (gemini.candidates != null && !gemini.candidates.isEmpty()) {
                GeminiCandidate candidate = gemini.candidates.get(0);

                // Log if the model hit the token limit — helps diagnose
                // future truncation issues without guessing.
                if ("MAX_TOKENS".equals(candidate.finishReason)) {
                    log.warn("Gemini hit MAX_TOKENS — response may be truncated. " +
                             "Increase maxOutputTokens or simplify the prompt.");
                }

                if (candidate.content != null && candidate.content.parts != null) {
                    // Join ALL non-thought text parts. The old code used
                    // findFirst() which could grab a thinking part (internal
                    // reasoning, not the actual remark) or miss text split
                    // across multiple parts.
                    String raw = candidate.content.parts.stream()
                            .filter(p -> p.text != null && !p.text.isBlank())
                            .filter(p -> p.thought == null || !p.thought)
                            .map(p -> p.text)
                            .collect(Collectors.joining(" "));

                    if (!raw.isBlank()) {
                        return cleanRemark(raw);
                    }
                }
            }

            throw new RuntimeException("Empty response from Gemini. The AI could not generate remarks — please try again.");

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Gemini API error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                throw new RuntimeException("AI rate limit reached. Free tier allows 10 requests/minute — please wait a moment and try again.");
            }
            throw new RuntimeException("AI service error: " + e.getStatusCode());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to generate AI remarks", e);
            throw new RuntimeException("Failed to generate remarks: " + e.getMessage());
        }
    }

    // ─── Post-processing ───────────────────────────────────────
    // Ensures the remark is clean, complete, and doesn't have artifacts
    // from the model's output (quotes, trailing whitespace, incomplete
    // last sentence).
    private String cleanRemark(String raw) {
        String text = raw.strip();
        // Strip wrapping quotation marks the model sometimes adds
        text = text.replaceAll("^\"|\"$", "").strip();
        text = text.replaceAll("^'|'$", "").strip();
        // Remove any markdown formatting
        text = text.replaceAll("\\*\\*", "").replaceAll("\\*", "");

        // If the remark doesn't end with proper punctuation, it was
        // likely truncated. Trim back to the last complete sentence.
        if (!text.isEmpty() && !text.matches(".*[.!?]$")) {
            int lastPeriod = text.lastIndexOf('.');
            int lastExcl   = text.lastIndexOf('!');
            int lastQ      = text.lastIndexOf('?');
            int lastEnd = Math.max(lastPeriod, Math.max(lastExcl, lastQ));
            if (lastEnd > 0) {
                text = text.substring(0, lastEnd + 1);
            } else {
                // No complete sentence at all — append a period
                text = text + ".";
            }
        }

        return text;
    }

    // ─── Prompt Engineering ────────────────────────────────────
    private String buildPrompt(RemarksRequest req) {
        StringBuilder p = new StringBuilder();

        p.append("You are an experienced CBSE school class teacher writing progress report remarks for a student.\n\n");

        p.append("STUDENT DETAILS:\n");
        p.append("- Full Name: ").append(safe(req.studentName)).append("\n");
        p.append("- First Name: ").append(safe(req.studentFirstName)).append("\n");
        p.append("- Class: ").append(safe(req.className)).append("\n");
        p.append("- Section: ").append(safe(req.section)).append("\n");

        if (req.attendancePct != null)
            p.append("- Attendance: ").append(req.attendancePct).append("%\n");
        if (req.averageMarks != null)
            p.append("- Average Marks: ").append(req.averageMarks).append("\n");
        if (req.overallPerformance != null)
            p.append("- Overall Performance: ").append(req.overallPerformance).append("\n");

        if (req.behaviour != null) {
            p.append("\nBEHAVIOUR SCORES (out of 5):\n");
            if (req.behaviour.discipline != null)     p.append("- Discipline: ").append(req.behaviour.discipline).append("/5\n");
            if (req.behaviour.homework != null)        p.append("- Homework: ").append(req.behaviour.homework).append("/5\n");
            if (req.behaviour.participation != null)   p.append("- Participation: ").append(req.behaviour.participation).append("/5\n");
            if (req.behaviour.punctuality != null)     p.append("- Punctuality: ").append(req.behaviour.punctuality).append("/5\n");
            if (req.behaviour.communication != null)   p.append("- Communication: ").append(req.behaviour.communication).append("/5\n");
            if (req.behaviour.teamwork != null)        p.append("- Teamwork: ").append(req.behaviour.teamwork).append("/5\n");
        }

        if (req.subjectPerformance != null && !req.subjectPerformance.isEmpty()) {
            p.append("\nSUBJECT PERFORMANCE:\n");
            req.subjectPerformance.forEach((subject, score) -> {
                if (score != null && !score.isBlank())
                    p.append("- ").append(subject).append(": ").append(score).append("\n");
            });
        }

        String style = (req.writingStyle != null && !req.writingStyle.isBlank())
                ? req.writingStyle : "warm and encouraging";
        p.append("\nWRITING STYLE: ").append(style).append("\n");

        p.append("\nRULES YOU MUST FOLLOW:\n");
        p.append("1. Write EXACTLY between 50 and 70 words. Count carefully.\n");
        p.append("2. Write in flowing prose — NO bullet points, NO numbered lists, NO line breaks.\n");
        p.append("3. DO NOT wrap the remark in quotation marks.\n");
        p.append("4. Refer to the student by their first name naturally.\n");
        p.append("5. Every remark must be completely unique — never repeat sentence structures, opening lines, or closing lines from any previous remark.\n");
        p.append("6. Vary your vocabulary, sentence length, and phrasing every time.\n");
        p.append("7. Sound like a real class teacher handwriting remarks on a report card — not like AI-generated text.\n");
        p.append("8. Mention the student's strongest subjects naturally within the prose.\n");
        p.append("9. If any subject is weak (AVERAGE or NEEDS_IMPROVEMENT, or marks below 50), mention it politely and encouragingly — never harshly.\n");
        p.append("10. Mention attendance ONLY if it is below 90%. If 90% or above, do not mention attendance at all.\n");
        p.append("11. Weave behaviour observations naturally into the remark — do not list them.\n");
        p.append("12. Use positive, encouraging, professional language suitable for an official CBSE report card.\n");
        p.append("13. Do NOT start with the word \"Overall\" or \"The student\".\n");
        p.append("14. Match the writing style parameter: if formal, use polished language; if warm, use nurturing tone; if concise, keep it tight.\n");
        p.append("15. End with a forward-looking or encouraging closing that feels natural — never formulaic.\n");
        p.append("16. IMPORTANT: Your remark MUST end with a complete sentence ending in a period. Never stop mid-sentence.\n");

        p.append("\nGenerate the remark now. Output ONLY the remark text, nothing else.");

        return p.toString();
    }

    private String safe(String val) {
        return (val != null && !val.isBlank()) ? val : "N/A";
    }
}

// ─── Controller (endpoint unchanged) ──────────────────────────
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
