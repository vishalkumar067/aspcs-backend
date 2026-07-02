package com.aspcs.chat;

import com.aspcs.common.ApiResponse;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

// ─── DTOs ──────────────────────────────────────────────────────
class ChatMessage {
    public String role;    // "user" or "model"
    public String content;
}

class ChatRequest {
    public String message;
    public List<ChatMessage> history; // previous turns, optional
}

class ChatResponse {
    public String reply;
    ChatResponse(String reply) { this.reply = reply; }
}

// ─── Gemini response models ────────────────────────────────────
@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiChatResponse {
    public List<GeminiChatCandidate> candidates;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiChatCandidate {
    public GeminiChatContent content;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiChatContent {
    public List<GeminiChatPart> parts;
}

@JsonIgnoreProperties(ignoreUnknown = true)
class GeminiChatPart {
    public String text;
}

// ─── Service ───────────────────────────────────────────────────
@Slf4j
@org.springframework.stereotype.Service
class ChatService {

    @Value("${app.ai.gemini-api-key:}")
    private String apiKey;

    @Value("${app.ai.gemini-model:gemini-2.5-flash}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String API_BASE = "https://generativelanguage.googleapis.com/v1beta/models/";

    private static final String SYSTEM_INSTRUCTION =
            "You are a helpful, friendly AI assistant for Acharya Shree Sudarshan Patna Central School (ASPCS), " +
            "a CBSE-affiliated school located in Patna, Bihar, India. " +
            "You can answer general questions on any topic — academics, science, math, history, current affairs, " +
            "coding, career guidance, parenting tips, or anything else a student, parent, or teacher might ask. " +
            "When questions are about the school specifically (admissions, fees, timings, facilities), answer helpfully " +
            "but mention that the user should contact the school office for the most current details. " +
            "Keep responses concise and clear — 2 to 4 sentences for simple questions, longer only when the topic genuinely needs it. " +
            "Be warm, professional, and supportive. Use simple English that's accessible to all. " +
            "If someone asks who you are, say you are the ASPCS AI Assistant.";

    public String chat(ChatRequest req) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("Chatbot requires a Gemini API key. Set GEMINI_API_KEY on Railway.");
        }
        if (req.message == null || req.message.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty");
        }

        try {
            String url = API_BASE + model + ":generateContent";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-goog-api-key", apiKey);

            // Build conversation contents with history
            List<Map<String, Object>> contents = new ArrayList<>();

            // Add conversation history if provided
            if (req.history != null) {
                for (ChatMessage msg : req.history) {
                    String geminiRole = "user".equals(msg.role) ? "user" : "model";
                    contents.add(Map.of(
                            "role", geminiRole,
                            "parts", List.of(Map.of("text", msg.content))
                    ));
                }
            }

            // Add the current message
            contents.add(Map.of(
                    "role", "user",
                    "parts", List.of(Map.of("text", req.message))
            ));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("system_instruction", Map.of(
                    "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
            ));
            body.put("contents", contents);
            body.put("generationConfig", Map.of(
                    "temperature", 0.8,
                    "maxOutputTokens", 1024
            ));

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            GeminiChatResponse gemini = objectMapper.readValue(response.getBody(), GeminiChatResponse.class);

            if (gemini.candidates != null && !gemini.candidates.isEmpty()) {
                GeminiChatCandidate candidate = gemini.candidates.get(0);
                if (candidate.content != null && candidate.content.parts != null) {
                    return candidate.content.parts.stream()
                            .filter(p -> p.text != null)
                            .map(p -> p.text)
                            .findFirst()
                            .orElse("I'm sorry, I couldn't generate a response. Please try again.");
                }
            }

            return "I'm sorry, I couldn't generate a response. Please try again.";

        } catch (org.springframework.web.client.HttpClientErrorException e) {
            log.error("Gemini chat error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            if (e.getStatusCode().value() == 429) {
                return "I'm receiving a lot of questions right now. Please wait a moment and try again.";
            }
            throw new RuntimeException("Chat service error: " + e.getStatusCode());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat error", e);
            throw new RuntimeException("Chat failed: " + e.getMessage());
        }
    }
}

// ─── Controller ────────────────────────────────────────────────
// Public — no auth required, so website visitors can use the chatbot
// without logging in. Rate limiting is handled by Gemini's own
// free-tier limits (10 RPM, 500 RPD).
@RestController
@RequestMapping("/chat")
class ChatController {

    private final ChatService service;

    ChatController(ChatService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@RequestBody ChatRequest req) {
        String reply = service.chat(req);
        return ResponseEntity.ok(ApiResponse.ok(new ChatResponse(reply)));
    }
}
