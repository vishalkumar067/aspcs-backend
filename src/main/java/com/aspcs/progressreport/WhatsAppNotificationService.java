package com.aspcs.progressreport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

record WhatsAppResult(boolean success, String providerRef, String errorMessage) {}

// Meta WhatsApp Cloud API adapter. Business-initiated notifications outside
// a live 24-hour conversation window MUST use a pre-approved message
// template (Meta requirement) — free-form text is rejected by the API in
// that case. This adapter therefore always sends via the "progress_report"
// template, not a raw text body, even though the stub-mode log shows the
// fully rendered text for readability.
//
// STUB MODE: app.whatsapp.enabled=false (the default until Meta Business
// verification completes) makes every "send" a no-op that only logs what
// would have been sent. Flip the flag once verified and the same code path
// starts actually calling Meta — no code changes needed at that point.
@Slf4j
@Service
class WhatsAppNotificationService {

    @Value("${app.whatsapp.enabled:false}")
    private boolean enabled;

    @Value("${app.whatsapp.phone-number-id:}")
    private String phoneNumberId;

    @Value("${app.whatsapp.access-token:}")
    private String accessToken;

    @Value("${app.whatsapp.api-version:v21.0}")
    private String apiVersion;

    // Must match a template already created + approved in Meta Business
    // Manager before this can go live. See the setup notes shared alongside
    // this code for the exact template body to submit for approval.
    private static final String TEMPLATE_NAME = "progress_report_notification";
    private static final String TEMPLATE_LANGUAGE = "en";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WhatsAppResult sendProgressReportNotification(
            String toPhone, String studentName, String className, String section,
            String cycleDateRange, String attendancePct, String overallPerformance) {

        String renderedPreview = "Dear Parent,\n\n" +
                "The Progress Report for " + studentName + " (" + formatClassSection(className, section) +
                ") for the period " + cycleDateRange +
                " has been emailed to your registered email address.\n\n" +
                "Attendance: " + attendancePct + "\n" +
                "Overall Performance: " + overallPerformance + "\n\n" +
                "Please check your email for the complete report.\n\n" +
                "Regards,\nASPCS";

        if (!enabled) {
            log.info("[WhatsApp STUB - not sent, app.whatsapp.enabled=false] To: {} | {}",
                    toPhone, renderedPreview.replace("\n", " "));
            return new WhatsAppResult(true, "stub-" + System.currentTimeMillis(), null);
        }

        if (toPhone == null || toPhone.isBlank()) {
            return new WhatsAppResult(false, null, "No parent WhatsApp number on file for this student");
        }
        if (phoneNumberId.isBlank() || accessToken.isBlank()) {
            return new WhatsAppResult(false, null, "WhatsApp credentials not configured");
        }

        try {
            String url = "https://graph.facebook.com/" + apiVersion + "/" + phoneNumberId + "/messages";

            Map<String, Object> payload = Map.of(
                "messaging_product", "whatsapp",
                "to", normalizePhone(toPhone),
                "type", "template",
                "template", Map.of(
                    "name", TEMPLATE_NAME,
                    "language", Map.of("code", TEMPLATE_LANGUAGE),
                    "components", List.of(Map.of(
                        "type", "body",
                        "parameters", List.of(
                            Map.of("type", "text", "text", studentName),
                            Map.of("type", "text", "text", formatClassSection(className, section)),
                            Map.of("type", "text", "text", cycleDateRange),
                            Map.of("type", "text", "text", attendancePct),
                            Map.of("type", "text", "text", overallPerformance)
                        )
                    ))
                )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    url, new HttpEntity<>(objectMapper.writeValueAsString(payload), headers), String.class);

            JsonNode body = objectMapper.readTree(response.getBody());
            String messageId = body.path("messages").path(0).path("id").asText(null);

            log.info("WhatsApp notification sent to {} for student {}", toPhone, studentName);
            return new WhatsAppResult(true, messageId, null);

        } catch (HttpClientErrorException e) {
            log.error("WhatsApp API error for {}: {} - {}", toPhone, e.getStatusCode(), e.getResponseBodyAsString());
            return new WhatsAppResult(false, null, "Meta API error: " + e.getStatusCode());
        } catch (Exception e) {
            log.error("WhatsApp send failure for {}: {}", toPhone, e.getMessage());
            return new WhatsAppResult(false, null, e.getMessage());
        }
    }

    private String formatClassSection(String className, String section) {
        return (section != null && !section.isBlank()) ? className + " - " + section : className;
    }

    // Meta requires E.164 format (e.g. 91XXXXXXXXXX, no leading +, no
    // spaces/dashes). Indian numbers stored as 10 digits get the country
    // code prefixed; numbers already prefixed are left alone.
    private String normalizePhone(String raw) {
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() == 10) return "91" + digits;
        return digits;
    }
}
